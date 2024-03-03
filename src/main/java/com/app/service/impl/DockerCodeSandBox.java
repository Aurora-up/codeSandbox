package com.app.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.json.JSONUtil;

import com.app.module.ProcessExecuteResult;
import com.app.module.LangType;
import com.app.module.debug.DebugRequest;
import com.app.module.debug.DebugResponse;
import com.app.module.execute.RequestArgs;
import com.app.module.execute.Response;
import com.app.module.judge.JudgeRequest;
import com.app.module.judge.JudgeResponse;
import com.app.service.CodeSandBox;
import com.app.utils.CodeLangAdaptUtil;
import com.app.utils.ProcessUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.BuildImageResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import lombok.var;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * @author HDD
 * @date 2024年02月04日
 * @description 使用 Docker 隔离运行环境的代码沙箱
 */
@Component
@Slf4j
@SuppressWarnings("deprecation")
public class DockerCodeSandBox implements CodeSandBox {
	/* Docker 沙箱信息 */
	private static final String ENVIRONMENT_DOCKER_IMAGE = "sandbox:1.0";
	private static final String ENVIRONMENT_CONTAINER_NAME = "sandbox";
	private static final String CODE_STORE_ROOT_PATH = "tempCodeRepository";
	private static final String VOLUMN_CODE_STORE_ROOT_PATH = "/codeStore";
	/* 测试数据文件前缀 */
	private static final String INPUT_NAME_PREFIX = "input-";
	/* 代码调试限制 (相对宽松)  */ 
	private static final Long TIME_LIMIT = 2000L; // 2s
	private static final Long Memory_LIMIT = 128 * 1024 * 1024L; // 128MB

	private static DockerClient dockerClient;
	static {
		/* 创建代码存放的 "根目录" 的绝对路径 */
		String projectDirPath = System.getProperty("user.dir");
		String codeStoreRootPath = projectDirPath + File.separator + CODE_STORE_ROOT_PATH;
		if (!FileUtil.exist(codeStoreRootPath)) {
			FileUtil.mkdir(codeStoreRootPath);
		}
		/* 初始化 Docker 客户端 */
		try {
			DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
			DockerHttpClient dockerHttpClient = new ApacheDockerHttpClient.Builder()
																		.dockerHost(config.getDockerHost())
																		.sslConfig(config.getSSLConfig())
																		.maxConnections(3000) // 最大连接数根据需求设定
																		.build();
			dockerClient = DockerClientBuilder.getInstance(config).withDockerHttpClient(dockerHttpClient).build();
		} catch (Exception e) {
			log.error("初始化 Docker Client 错误", e);
		}
		// todo 检查 tempCodeRespority 下的 exectue_core 是否存在
		// 若不存在则编译 rust 项目至该目录下
		
	}

	/**
	 * 代码调试
	 * 
	 * @param debugRequest 代码调试请求
	 * @return 代码调试结果 
	 */
	@Override
	public DebugResponse codeDebug(DebugRequest debugRequest) {
		var respBuilder = DebugResponse.builder();
		String code = Base64.decodeStr(debugRequest.getCode());
		String lang = debugRequest.getLang();
		String input = Base64.decodeStr(debugRequest.getInput());
		List<String> inputList = new ArrayList<>();
		if (input != null) {
			inputList.add(input.trim());
		} else {
			inputList.add("");
		}

		/* 1. 用户代码隔离 */
		Path codeFileParentDir = tackleCodeStorageAndIsolation(code, inputList, lang, TIME_LIMIT, Memory_LIMIT);

		/* 2. 代码编译 */
		var codeCompileResult = codeCompile(codeFileParentDir.toString(), lang);

		// 编译失败 (Compiler Error)
		if (codeCompileResult.getExitValue() != 0) {
			/* 代码编译错误输出过滤 */
			BiFunction<String, String, String> tackleOutput = (String errorResult, String langType) -> {
				String perfix = new String("main");
				String subfix = new String();
				switch (langType) {
					case "java":
						perfix = "Main";
						subfix = ".java";
						break;
					case "cpp":
						subfix = ".cpp";
						break;
					case "c":
						subfix = ".c";
						break;
					case "rust":
						subfix = ".rs";
						break;
					default:
						break;
				}
				String[] compileError = errorResult.split(perfix + subfix);
				if (compileError.length >= 2) {
					return perfix + subfix + compileError[1];
				}
				return errorResult;
			};
			codeFileClean(codeFileParentDir.toString());
			return respBuilder.resultStatus(1001)
					.resultMessage(Base64.encode(tackleOutput.apply(codeCompileResult.getErrorResult(), lang)))
					.build();
		}

		/* 3. 启动 Docker 容器 */
		String containerId = getContainerId(DockerCodeSandBox.dockerClient, codeFileParentDir);
		var containerInfo = DockerCodeSandBox.dockerClient.inspectContainerCmd(containerId).exec();
		if (Boolean.FALSE.equals(containerInfo.getState().getRunning())) {
			DockerCodeSandBox.dockerClient.startContainerCmd(containerId).exec(); // 启动容器
		}

		/* 4. 代码运行 */
		var codeRunResults = DockerCodeSandBox.codeRun(DockerCodeSandBox.dockerClient, containerId, codeFileParentDir);
		
		var debugResponse = new DebugResponse();
		Response codeRunResult = new Response();
		try {
			codeRunResult = codeRunResults.get(0);
		} catch (IndexOutOfBoundsException e) {
			log.error("代码运行结果返回为空, 导致在 codeDebug 中出现结果数组访问越界异常. ", e);
		}
		Integer exit_code = codeRunResult.getExit_code();
		String output_msg = codeRunResult.getOutput_msg();
		Long time = codeRunResult.getTime();
		Long memory = codeRunResult.getMemory();
		/* 判断系统出错 */
		if (exit_code == 500) {
			debugResponse = respBuilder.resultStatus(500)
															.resultMessage(Base64.encode("Judge System Error"))
															.build();
		}
		/* 越权操作 */
		else if (exit_code == 1) {
			String permissionDenyInfo = codeRunResult.getOutput_msg();

			/* java 代码权限异常输出过滤 */
			Function<String, Object> isPermissionDenyInfo = (String errorResult) -> {
				String[] permissionException = errorResult.split("#as#");
				if (permissionException.length >= 2) {
					return permissionException[1];
				}
				return null;
			};
			var permissionMessage = isPermissionDenyInfo.apply(Base64.decodeStr(permissionDenyInfo));
			debugResponse =  respBuilder.resultStatus(1)
					.time(codeRunResult.getTime())
					.memory(codeRunResult.getMemory())
					.resultMessage(Base64.encode(permissionMessage.toString()))
					.build();
		}
		/* 判断系统正常运行 */
		else if (exit_code == 1000) {
			debugResponse = respBuilder.resultStatus(1000)
						.resultMessage(output_msg)
						.time(time)
						.memory(memory)
						.build();
		}
		/* 运行时错误 */
		else if (exit_code == 1002) {
			/* java 代码运行时异常输出过滤 */
			Function<String, String> tackleRuntimeErrorOutput = (String errorResult) -> {
				String[] runtimeException = errorResult.split("release");
				if (runtimeException.length >= 2) {
					return runtimeException[1];
				}
				return errorResult;				
			};
			debugResponse = respBuilder.resultStatus(1002)
						.resultMessage(Base64.encode(tackleRuntimeErrorOutput.apply(Base64.decodeStr(output_msg))))
						.time(time)
						.memory(memory)
						.build();
		}
		/* 运行超时 */
		else if (exit_code == 1003) {
			debugResponse = respBuilder.resultStatus(1003)
						.resultMessage(output_msg)
						.time(-1L)
						.memory(memory)
						.build();
		}
		/* 运行占用内存超出限制 */
		else if (exit_code == 1004) {
			debugResponse = respBuilder.resultStatus(1004)
						.resultMessage(output_msg)
						.time(time)
						.memory(-1L)
						.build();
		}
		/* 未知错误 */
		else {
			debugResponse = respBuilder.resultStatus(777)
						.resultMessage(Base64.encode("未知错误: ") + output_msg)
						.time(time)
						.memory(memory)
						.build();
		}
		codeFileClean(codeFileParentDir.toString());
		return debugResponse;
	}

	/**
	 * 代码评审
	 *
	 * @param judgeRequest 代码评审请求
	 * @return 代码评审结果
	 */
	@Override
	public JudgeResponse codeJudge(JudgeRequest judgeRequest) {
		var JRBuilder = JudgeResponse.builder();
		String code = Base64.decodeStr(judgeRequest.getCode()); // ---------------------
		String lang = judgeRequest.getLang();
		Long timeLimit = judgeRequest.getTimeLimit();
		Long memoryLimit = judgeRequest.getMemoryLimit();
		List<String> inputList = judgeRequest.getTestCases().stream().map(e -> {
			return Base64.decodeStr(e.getInput()); // --------------------
		}).toList();
		Path codeFileParentDir = tackleCodeStorageAndIsolation(code, inputList, lang, timeLimit, memoryLimit);
		/* 2. 代码编译 */
		var codeCompileResult = codeCompile(codeFileParentDir.toString(), lang);
		// 编译失败 (Compiler Error)
		if (codeCompileResult.getExitValue() != 0) {
			/* 代码编译错误输出过滤 */
			BiFunction<String, String, String> tackleOutput = (String errorResult, String langType) -> {
				String perfix = new String("main");
				String subfix = new String();
				switch (langType) {
					case "java":
						perfix = "Main";
						subfix = ".java";
						break;
					case "cpp":
						subfix = ".cpp";
						break;
					case "c":
						subfix = ".c";
						break;
					case "rust":
						subfix = ".rs";
						break;
					default:
						break;
				}
				String[] compileError = errorResult.split(perfix + subfix);
				if (compileError.length >= 2) {
					return perfix + subfix + compileError[1];
				}
				return errorResult;
			};
			codeFileClean(codeFileParentDir.toString());
			return JRBuilder.resultStatus(1001)
					.resultMessage(Base64.encode(tackleOutput.apply(codeCompileResult.getErrorResult(), lang)))
					.build();
		}

		// 构建测试数据的ID 和 其正确结果之间的 HashMap
		HashMap<Integer, String> mp = new HashMap<>();
		judgeRequest.getTestCases().forEach(testCase -> mp.put(testCase.getId(), testCase.getCorrectResult()));

		/* 3. 启动 Docker 容器 */
		String containerId = getContainerId(DockerCodeSandBox.dockerClient, codeFileParentDir);
		var containerInfo = DockerCodeSandBox.dockerClient.inspectContainerCmd(containerId).exec();
		if (Boolean.FALSE.equals(containerInfo.getState().getRunning())) {
			DockerCodeSandBox.dockerClient.startContainerCmd(containerId).exec(); // 启动容器
		}
		/* 4. 代码运行 */
		var codeRunResults = DockerCodeSandBox.codeRun(DockerCodeSandBox.dockerClient, containerId, codeFileParentDir);
		var judgeResponse = new JudgeResponse();
		var codeRunResultFirst = new Response();
		try {
			codeRunResultFirst = codeRunResults.get(0);
		} catch (IndexOutOfBoundsException e) {
			log.error("代码运行结果返回为空, 导致在 codeRun 中出现结果数组访问越界异常. ", e);
		}
		/* 判题系统出错 */
		if (codeRunResultFirst.getExit_code() == 500) {
			judgeResponse = JRBuilder.resultStatus(500)
															.resultMessage(Base64.encode("Judge System Error"))
															.build();
		}else {
			Integer passTestCasesNumber = 0;
			Comparator<Response> testCaseIdComparator = Comparator.comparing(Response::getTest_case_id, Comparator.naturalOrder());
			codeRunResults.sort(testCaseIdComparator);
			Long time = 0L;
			Long memory = 0L;
			for (var response : codeRunResults) {
				Integer exit_code = response.getExit_code();
				Integer test_case_id = response.getTest_case_id();
				String output_msg = response.getOutput_msg();
				/* 代码正常执行 */
				if (exit_code == 1000) {
					// 定义匹配由空格、换行符或制表符隔开的内容的正则表达式
					String regex = "\\s+";
					Pattern pattern = Pattern.compile(regex);
					String[] fixOutputMsg = pattern.split(Base64.decodeStr(output_msg));
					// 当前数据通过
					if (Base64.decodeStr(output_msg).equals(mp.get(test_case_id))) {
						passTestCasesNumber ++;
						time += response.getTime();  // 时间累加
						memory = Math.max(memory, response.getMemory()); // 内存使用峰值内存
					}
					// PE
					else if (ArrayUtil.equals(fixOutputMsg, pattern.split(mp.get(test_case_id)))) {
						judgeResponse = JRBuilder.resultStatus(1006)
																		.passTestCasesNumber(passTestCasesNumber)
																		.noPassTestCaseId(response.getTest_case_id())
																		.time(response.getTime())
																		.memory(response.getMemory())
																		.resultMessage(Base64.encode("Presentation Error"))
																		.build();
						break;
					}
					// WA
					else {
						judgeResponse = JRBuilder.resultStatus(1005)
																		.passTestCasesNumber(passTestCasesNumber)
																		.noPassTestCaseId(response.getTest_case_id())
																		.time(response.getTime())
																		.memory(response.getMemory())
																		.resultMessage(Base64.encode("Wrong Answer"))
																		.build();
						break;		
					}
				}
				/* 运行时错误 */
				else if (exit_code == 1002) {
					/* java 代码运行时异常输出过滤 */
					Function<String, String> tackleRuntimeErrorOutput = (String errorResult) -> {
						String[] runtimeException = errorResult.split("release");
						if (runtimeException.length >= 2) {
							return runtimeException[1];
						}
						return errorResult;	
					};
					String fixOutput = tackleRuntimeErrorOutput.apply(Base64.decodeStr(output_msg));
					judgeResponse = JRBuilder.resultStatus(1002)
																	.passTestCasesNumber(passTestCasesNumber)
																	.noPassTestCaseId(response.getTest_case_id())
																	.resultMessage(Base64.encode(fixOutput))
																	.build();
					break;
				}
				/* 运行超时 */
				else if (exit_code == 1003) {
					judgeResponse = JRBuilder.resultStatus(1003)
																	.passTestCasesNumber(passTestCasesNumber)
																	.noPassTestCaseId(response.getTest_case_id())
																	.time(-1L)
																	.memory(response.getMemory())
																	.resultMessage(Base64.encode("Time Limit Exceeded"))
																	.build();
					break;
				}
				/* 运行时占用内存超出限制 */
				else if (exit_code == 1004) {
					judgeResponse = JRBuilder.resultStatus(1004)
																	.passTestCasesNumber(passTestCasesNumber)
																	.noPassTestCaseId(response.getTest_case_id())
																	.time(response.getTime())
																	.memory(-1L)
																	.resultMessage(Base64.encode("Memory Limit Exceeded"))
																	.build();
					break;
				}
				/* 越权操作 */
				else if (exit_code == 1) {
					String permissionDenyInfo = response.getOutput_msg();

					/* java 代码权限异常输出过滤 */
					Function<String, Object> isPermissionDenyInfo = (String errorResult) -> {
						String[] permissionException = errorResult.split("#as#");
						if (permissionException.length >= 2) {
							return permissionException[1];
						}
						return null;
					};
					var permissionMessage = isPermissionDenyInfo.apply(Base64.decodeStr(permissionDenyInfo));
					judgeResponse = JRBuilder.resultStatus(1)
																	.passTestCasesNumber(passTestCasesNumber)
																	.noPassTestCaseId(response.getTest_case_id())
																	.time(response.getTime())
																	.memory(response.getMemory())
																	.resultMessage(Base64.encode("Permission Deny: " + permissionMessage))
																	.build();
					break;
				}
				/* 未知错误 */
				else {
					judgeResponse = JRBuilder.resultStatus(777)
																	.passTestCasesNumber(passTestCasesNumber)
																	.noPassTestCaseId(response.getTest_case_id())
																	.resultMessage(Base64.encode("Unknown Error: ") + output_msg)
																	.time(response.getTime())
																	.memory(response.getMemory())
																	.build();
					break;
				}
			}
			/* AC */
			if (passTestCasesNumber == codeRunResults.size()) {
				judgeResponse = JRBuilder.resultStatus(1000)
													.passTestCasesNumber(passTestCasesNumber)
													.noPassTestCaseId(0)
													.resultMessage(Base64.encode("Accepted"))
													.time(time)
													.memory(memory)
													.build();
			}
		}
		codeFileClean(codeFileParentDir.toString());
		return judgeResponse;
	}

	/**
	 * 处理用户代码的存储隔离
	 *
	 * @param code 用户提交的代码
	 * @param inputList 输入列表
	 * @param lang 编程语言
	 * @param timieLimit 时间限制
	 * @param memoryLimit 内存限制
	 * @return 用户提交代码存放的目录
	 */
	private static Path tackleCodeStorageAndIsolation(String code, List<String> inputList, String lang, Long timieLimit, Long memoryLimit) {
		/* 1. 创建代码存放的 "根目录" 的绝对路径 */
		String projectDirPath = System.getProperty("user.dir");
		String codeStoreRootPath = projectDirPath + File.separator + CODE_STORE_ROOT_PATH;

		/* 2. 隔离用户提交的代码文件和测试数据在单独目录 */
		String isolcationDirName = UUID.randomUUID().toString();
		String userCodeIsolationDirPath = codeStoreRootPath + File.separator + isolcationDirName;
		String CODE_FILE_NAME = null;
		CODE_FILE_NAME = CodeLangAdaptUtil.codeStoreFileNameAdapt(LangType.getByLangName(lang));
		String userCodeFilePath = userCodeIsolationDirPath + File.separator + CODE_FILE_NAME;

		// 创建 main.c / main.cpp / main.rs / Main.java / main.py 文件
		File userCodeFile = FileUtil.writeString(code, userCodeFilePath, StandardCharsets.UTF_8);
		// 创建测试数据文件
		for (int i = 0; i < inputList.size(); i++) {
			String inputFilePath = userCodeIsolationDirPath + File.separator + INPUT_NAME_PREFIX + (i + 1) + ".txt";
			FileUtil.writeString(inputList.get(i).trim(), inputFilePath, StandardCharsets.UTF_8);
		}
		// 封装 执行请求的 json 文件
		Function<String, Integer> langMap = (String langType) -> {
			if (langType.equals("java")) return 2;
			else if (langType.equals("python")) return 3;
			else return 1;
		};
		var requestArgsbuilder = RequestArgs.builder();
		var requestArgs = requestArgsbuilder
			.time_limit(timieLimit)
			.memory_limit(memoryLimit)
			.file_dir(VOLUMN_CODE_STORE_ROOT_PATH + File.separator + isolcationDirName + File.separator)
			.test_case_num(Math.max(1, inputList.size()))
			.lang(langMap.apply(lang))
			.build();
		String jsonString = JSONUtil.toJsonStr(requestArgs);
		String jsonFilePath = "request_args.json";
		FileUtil.writeUtf8String(jsonString, userCodeIsolationDirPath + File.separator + jsonFilePath);
		// 封装 file_dir.txt
		FileUtil.writeString(VOLUMN_CODE_STORE_ROOT_PATH + File.separator + isolcationDirName + File.separator,
												 userCodeIsolationDirPath + File.separator + "file-dir.txt", StandardCharsets.UTF_8);
		/* 返回用户提交的代码文件所在的目录 */
		return Paths.get(userCodeFile.getParentFile().getAbsolutePath());
	}

	/**
	 * cpp / c / rust / java 代码编译 (python 不需要编译)
	 *
	 * @param codeFileParentDir 待编译代码文件存储目录
	 * @param lang 编程语言
	 * @return 编译结果信息
	 */
	private static ProcessExecuteResult codeCompile(String codeFileParentDir, String lang) {
		var messageBuild = ProcessExecuteResult.builder();
		String[] compileCommand = new String[]{};
		/* 匹配对应编程语言的编译命令 */
		if (!lang.equals("python")) {
			compileCommand = CodeLangAdaptUtil.codeCompileCommandArgsAdapt(LangType.getByLangName(lang), codeFileParentDir);
		}
		var processBuilder = new ProcessBuilder(compileCommand);
		try {
			Process compileProcess = processBuilder.start();
			int exitValue = compileProcess.waitFor();
			ProcessExecuteResult message = new ProcessExecuteResult();
			/* 编译成功 */
			if (exitValue == 0) {
				message = messageBuild.exitValue(0)
					.normalResult(ProcessUtil.getProcessOutput(compileProcess.getInputStream(), exitValue))
					.build();
			}
			/* 编译失败 */
			else {
				message = messageBuild.exitValue(1001)
					.errorResult(ProcessUtil.getProcessOutput(compileProcess.getErrorStream(), exitValue))
					.build();
			}
			compileProcess.destroy();
			return message;
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
		return messageBuild.build();
	}

	/**
	 * 根据 Dockerfile 文件创建代码沙箱环境的镜像
	 * @param dockerClient docker 客户端
	 */
	private static void createSandBoxImage(DockerClient dockerClient) {
		String projectDirPath = System.getProperty("user.dir");
		File Dockerfile = new File(projectDirPath + File.separator + "Dockerfile");
		BuildImageCmd buildImageCmd = dockerClient.buildImageCmd(Dockerfile);
		Set<String> name = new HashSet<String>();
		name.add("sandbox:1.0");
		buildImageCmd.withTags(name);
		var buildImageResultCallback = new BuildImageResultCallback() {
			@Override
			public void onNext(BuildResponseItem item) {
				log.info("创建代码沙箱环境镜像中: " + item.toString());
				super.onNext(item);
			}
		};
		try {
			buildImageCmd.exec(buildImageResultCallback)
					.awaitCompletion();
			log.info("代码沙箱创建成功");
		} catch (InterruptedException e) {
			log.error("创建代码沙箱环境镜像失败");
			e.printStackTrace();
		}
	}

	/**
	 * 利用沙箱环境镜像创建容器
	 *
	 * @param codeFileParentDir 要挂载在容器 /codeStore 下的目录
	 * @param dockerClient      docker 客户端
	 * @return 已创建的容器的 ID
	 */
	private static String createSandBoxContainer(Path codeFileParentDir, DockerClient dockerClient) {
		var containerCmd = dockerClient.createContainerCmd(ENVIRONMENT_DOCKER_IMAGE).withName(ENVIRONMENT_CONTAINER_NAME);
		var hostConfig = new HostConfig();
		log.info("挂载目录:" + codeFileParentDir.getParent().toString());
		hostConfig.setBinds(new Bind(codeFileParentDir.getParent().toString(), new Volume("/codeStore")));
		hostConfig.withMemory(256 * 1024 * 1024L);
		hostConfig.withCpuCount(1L);
		
		String seccompProfile = null;
		try {
			String projectDir = System.getProperty("user.dir");
			String seccompProfilePath = projectDir + File.separator + "src" + File.separator + "main" + File.separator + "resources" + File.separator + "permission" + File.separator + "seccomp_profile_for_container.json";
			seccompProfile = new String(Files.readAllBytes(Paths.get(seccompProfilePath)));
		} catch (IOException e) {
			log.error("无法读取到 seccomp 安全配置文件", e);
			e.printStackTrace();
		}
		hostConfig.withSecurityOpts(List.of("seccomp=" + seccompProfile));
		
		var containerInstance = containerCmd
				.withReadonlyRootfs(true)
				.withHostConfig(hostConfig)
				.withNetworkDisabled(true)
				.withAttachStdin(true)
				.withAttachStdout(true)
				.withAttachStderr(true)
				.withTty(true)
				.exec();
		return containerInstance.getId();
	}

	/**
	 * 获取容器ID
	 *
	 * @param dockerClient      docker 客户端
	 * @param codeFileParentDir 挂载目录
	 * @return 创建好的容器ID
	 */
	private static String getContainerId(DockerClient dockerClient, Path codeFileParentDir) {
		var listContainersCmd = dockerClient.listContainersCmd().withNameFilter(List.of(ENVIRONMENT_CONTAINER_NAME))
				.withShowAll(true);
		var listImageCmd = dockerClient.listImagesCmd().withReferenceFilter(ENVIRONMENT_DOCKER_IMAGE);

		List<Image> existedImage = listImageCmd.exec();
		List<Container> existedContainer = listContainersCmd.exec();

		String containerId;
		if (existedImage.isEmpty() && existedContainer.isEmpty()) {
			createSandBoxImage(dockerClient);
			containerId = createSandBoxContainer(codeFileParentDir, dockerClient);
		} else if (!existedImage.isEmpty() && existedContainer.isEmpty()) {
			containerId = createSandBoxContainer(codeFileParentDir, dockerClient);
		} else {
			containerId = existedContainer.get(0).getId();
		}
		return containerId;
	}

	/**
	 * 代码运行
	 *
	 * @param dockerClient      docker 客户端
	 * @param containerId       沙箱容器 ID
	 * @param codeFileParentDir 容器挂载目录
	 * @return 运行结果信息
	 */
	private static List<Response> codeRun(DockerClient dockerClient, String containerId, Path codeFileParentDir) {
		List<Response> messages = new ArrayList<>();

		/* java 代码运行时权限限制 */
		String permissionCheckFilePath = System.getProperty("user.dir") + File.separator + "tempCodeRepository"
				+ File.separator + "DenyPermission.class";
		if (!Files.exists(Paths.get(permissionCheckFilePath))) {
			compileDenyPermissionFile();
		}

		/* 启动 execute_core 执行代码 */
		String[] runCommand = new String[] { "docker", "exec", "-i", containerId, 
            "/codeStore" + File.separator + "execute_core" + File.separator + "execute_core"};
		var processBuilder = new ProcessBuilder(runCommand);
		processBuilder.redirectInput(new File(codeFileParentDir + File.separator + "file-dir.txt"));
		try {
			Process runProcess = processBuilder.start();
			int exitValue = runProcess.waitFor();
			/* execute_core 正常运行 */ 
			if (exitValue == 0) {
				String normalOutput = ProcessUtil.getProcessOutput(runProcess.getInputStream(), exitValue);
				normalOutput = Base64.decodeStr(normalOutput);
				List<Response> exec_resp = JSONUtil.toList(JSONUtil.parseArray(normalOutput), Response.class);
				// exec_resp.forEach(System.out::println);
				return exec_resp;
			}
			/* execute_core 系统异常 (500 错误) */
			else {
				String errorOutput = ProcessUtil.getProcessOutput(runProcess.getErrorStream(), exitValue);
				var RSBuilder = Response.builder();
				var response = RSBuilder.exit_code(500)
								 .output_msg(errorOutput)
								 .build();
				log.info("execute_core 出错: \n" + errorOutput);
				return List.of(response);
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
		return messages;
	}
	
	/**
	 * 编译 java 代码的权限校验文件
	 * 
	 * @return 编译后的 .class 文件所在目录
	 */
	private static String compileDenyPermissionFile() {
		String userDir = System.getProperty("user.dir");
		String classPath = userDir + File.separator + "tempCodeRepository";
		String javaPath = userDir + File.separator + "src/main/resources/permission/DenyPermission.java";
		var processBuilder = new ProcessBuilder(new String[] { "javac", "-d", classPath, javaPath });
		try {
			Process compilePermissionCheckFile = processBuilder.start();
			compilePermissionCheckFile.waitFor();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
		return classPath;
	}

	/**
	 * 用户代码文件清理
	 *
	 * @param codeFileParentDir 代码文件存放目录
	 */
	private static void codeFileClean(String codeFileParentDir) {
		try {
			Path directoryToDelete = Paths.get(codeFileParentDir);
			Files.walkFileTree(directoryToDelete, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
					new SimpleFileVisitor<>() {
						@Override
						public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
							Files.delete(file);
							return FileVisitResult.CONTINUE;
						}

						@Override
						public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
							Files.delete(dir);
							return FileVisitResult.CONTINUE;
						}
					});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}