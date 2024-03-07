package com.app.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.IdUtil;

import com.app.module.ProcessExecuteResult;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.app.common.LangType;
import com.app.common.StatusEnum;
import com.app.exception.BusinessException;
import com.app.module.debug.DebugRequest;
import com.app.module.debug.DebugResponse;
import com.app.module.execute.RequestArgs;
import com.app.module.execute.Response;
import com.app.module.judge.JudgeRequest;
import com.app.module.judge.JudgeResponse;
import com.app.service.CodeSandBox;
import com.app.utils.CodeLangAdaptUtil;
import com.app.utils.DockerUtil;
import com.app.utils.OupurFilterUtil;
import com.app.utils.ProcessUtil;

import lombok.var;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;


/**
 * @author HDD
 * @date 2024年02月04日
 * @description 使用 Docker 隔离运行环境的代码沙箱
 */
@Component
@Slf4j
public class DockerCodeSandBox implements CodeSandBox {
	/* Docker 沙箱信息 */
	private static final String SANDBOX_DOCKER_IMAGE = "sandbox:2.0";
	private static final String SANDBOX_CONTAINER_NAME = "sandbox";
	/* Docker 编译镜像/容器信息 */
	private static final String COMPILE_ENV_DOCKER_IMAGE = "compile_env:1.0";
	private static final String COMPILE_ENV_CONTAINER_NAME = "compile_env";
	private static final String CODE_STORE_ROOT_PATH = "tempCodeRepository";
	private static final String VOLUMN_CODE_STORE_ROOT_PATH = "/codeStore";
	/* 测试数据文件前缀 */
	private static final String INPUT_NAME_PREFIX = "input-";
	/* 代码调试限制 (相对宽松)  */ 
	private static final Long TIME_LIMIT = 2000L; // 2s
	private static final Long Memory_LIMIT = 128 * 1024 * 1024L; // 128MB

	@Autowired
	DockerUtil dockerUtil;
	
	/**
	 * 代码调试
	 * 
	 * @param debugRequest 代码调试请求
	 * @return 代码调试结果 
	 */
	@Override
	public DebugResponse codeDebug(DebugRequest debugRequest) {
		var DRBuilder = DebugResponse.builder();
		String code = Base64.decodeStr(debugRequest.getCode());
		String lang = debugRequest.getLang();
		String input = Base64.decodeStr(debugRequest.getInput());
		List<String> inputList = new ArrayList<>();
		if (input != null) {
			inputList.add(input.trim());
		} else {
			inputList.add("");
		}

		/* 1. 代码存储隔离 */
		Path codeFileParentDir = null;
		Pair<Path, String> ans  = tackleCodeStorageAndIsolation(code, inputList, lang, TIME_LIMIT, Memory_LIMIT);
		codeFileParentDir = ans.getKey();

		/* 2. 启动编译容器，代码编译 */
		String compileContainerId = dockerUtil.getContainerId(codeFileParentDir, COMPILE_ENV_DOCKER_IMAGE, COMPILE_ENV_CONTAINER_NAME, 0);
		var codeCompileResult = codeCompile(codeFileParentDir.toString(), lang, compileContainerId, ans.getValue());

		// 编译失败 (Compiler Error)
		if (codeCompileResult.getExitValue() != 0) {
			/* 代码编译错误输出过滤 */
			String fixedCompileOutput = OupurFilterUtil.tackleCompileOutput(codeCompileResult.getErrorResult(), lang);
			codeFileClean(codeFileParentDir.toString());
			return DRBuilder.resultStatus(1001)
					.resultMessage(Base64.encode(fixedCompileOutput))
					.build();
		}

		/* 3. 启动沙箱容器 */
		String sandBoxContainerId = dockerUtil.getContainerId(codeFileParentDir, SANDBOX_DOCKER_IMAGE, SANDBOX_CONTAINER_NAME, 1);

		/* 4. 代码运行 */
		var codeRunResults = DockerCodeSandBox.codeRun(sandBoxContainerId, codeFileParentDir);
		
		var debugResponse = new DebugResponse();
		Response codeRunResult = new Response();
		try {
			codeRunResult = codeRunResults.get(0);
		} catch (IndexOutOfBoundsException e) {
			throw new BusinessException(StatusEnum.SYSTEM_ERROR, "代码运行结果返回为空, 导致在 codeDebug 中出现结果数组访问越界异常. " + e);
		}
		Integer exitCode = codeRunResult.getExitCode();
		String outputMsg = codeRunResult.getOutputMsg();
		Long time = codeRunResult.getTime();
		Long memory = codeRunResult.getMemory();
		/* 判断系统出错 */
		if (exitCode == 500) {
			debugResponse = DRBuilder.resultStatus(500)
															.resultMessage(Base64.encode("Judge System Error"))
															.build();
		}
		/* 越权操作 */
		else if (exitCode == 1) {
			debugResponse =  DRBuilder.resultStatus(1)
					.time(codeRunResult.getTime())
					.memory(codeRunResult.getMemory())
					.resultMessage(outputMsg)
					.build();
		}
		/* 判断系统正常运行 */
		else if (exitCode == 1000) {
			debugResponse = DRBuilder.resultStatus(1000)
						.resultMessage(outputMsg)
						.time(time)
						.memory(memory)
						.build();
		}
		/* 运行时错误 RE */
		else if (exitCode == 1002) {
			debugResponse = DRBuilder.resultStatus(1002)
						.resultMessage(outputMsg)
						.time(time)
						.memory(memory)
						.build();
		}
		/* 运行超时 TLE */
		else if (exitCode == 1003) {
			debugResponse = DRBuilder.resultStatus(1003)
						.resultMessage(outputMsg)
						.time(-1L)
						.memory(memory)
						.build();
		}
		/* 运行占用内存超出限制 MLE */
		else if (exitCode == 1004) {
			debugResponse = DRBuilder.resultStatus(1004)
						.resultMessage(outputMsg)
						.time(time)
						.memory(-1L)
						.build();
		}
		/* 未知错误 */
		else {
			debugResponse = DRBuilder.resultStatus(777)
						.resultMessage(Base64.encode("未知错误: ") + outputMsg)
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
		String code = Base64.decodeStr(judgeRequest.getCode());
		String lang = judgeRequest.getLang();
		Long timeLimit = judgeRequest.getTimeLimit();
		Long memoryLimit = judgeRequest.getMemoryLimit();
		List<String> inputList = judgeRequest.getTestCases().stream().map(e -> {
			return Base64.decodeStr(e.getInput());
		}).toList();

		/* 1. 代码存储隔离 */
		Path codeFileParentDir = null;
		Pair<Path, String> ans  = tackleCodeStorageAndIsolation(code, inputList, lang, timeLimit, memoryLimit);
		codeFileParentDir = ans.getKey();

		/* 2. 启动编译容器，代码编译 */
		String compileContainerId = dockerUtil.getContainerId(codeFileParentDir, COMPILE_ENV_DOCKER_IMAGE, COMPILE_ENV_CONTAINER_NAME, 0);
		var codeCompileResult = codeCompile(codeFileParentDir.toString(), lang, compileContainerId, ans.getValue());
		// 编译失败 (Compiler Error)
		if (codeCompileResult.getExitValue() != 0) {
			/* 代码编译错误输出过滤 */
			String fixedCompileOutput = OupurFilterUtil.tackleCompileOutput(codeCompileResult.getErrorResult(), lang);
			codeFileClean(codeFileParentDir.toString());
			return JRBuilder.resultStatus(1001)
					.resultMessage(Base64.encode(fixedCompileOutput))
					.build();
		}

		// 构建测试数据的ID 和 其正确结果之间的 HashMap
		HashMap<Integer, String> mp = new HashMap<>();
		judgeRequest.getTestCases().forEach(testCase -> mp.put(testCase.getId(), testCase.getCorrectResult()));

		/* 3. 启动沙箱容器 */
		String sandBoxContainerId = dockerUtil.getContainerId(codeFileParentDir, SANDBOX_DOCKER_IMAGE, SANDBOX_CONTAINER_NAME, 1);

		/* 4. 代码运行 */
		var codeRunResults = DockerCodeSandBox.codeRun(sandBoxContainerId, codeFileParentDir);
		var judgeResponse = new JudgeResponse();
		var codeRunResultFirst = new Response();
		try {
			codeRunResultFirst = codeRunResults.get(0);
		} catch (IndexOutOfBoundsException e) {
			throw new BusinessException(StatusEnum.SYSTEM_ERROR, "代码运行结果返回为空, 导致在 codeRun 中出现结果数组访问越界异常. " + e);
		}
		/* 判题系统出错 */
		if (codeRunResultFirst.getExitCode() == 500) {
			judgeResponse = JRBuilder.resultStatus(500)
															.resultMessage(Base64.encode("Judge System Error"))
															.build();
		}
		/* 判题系统正常运行 */
		else {
			Integer passTestCasesNumber = 0;
			Comparator<Response> testCaseIdComparator = Comparator.comparing(Response::getTestCaseId, Comparator.naturalOrder());
			codeRunResults.sort(testCaseIdComparator);
			Long time = 0L;
			Long memory = 0L;
			for (var response : codeRunResults) {
				Integer exitCode = response.getExitCode();
				Integer testCaseId = response.getTestCaseId();
				String outputMsg = response.getOutputMsg();
				/* 代码正常执行 */
				if (exitCode == 1000) {
					// 定义匹配由空格、换行符或制表符隔开的内容的正则表达式
					String regex = "\\s+";
					Pattern pattern = Pattern.compile(regex);
					String[] fixOutputMsg = pattern.split(Base64.decodeStr(outputMsg));
					// 当前数据通过
					if (Base64.decodeStr(outputMsg).equals(mp.get(testCaseId))) {
						passTestCasesNumber ++;
						time += response.getTime();  // 时间累加
						memory = Math.max(memory, response.getMemory()); // 内存使用峰值内存
					}
					// 输出格式错误 PE
					else if (ArrayUtil.equals(fixOutputMsg, pattern.split(mp.get(testCaseId)))) {
						judgeResponse = JRBuilder.resultStatus(1006)
																		.passTestCasesNumber(passTestCasesNumber)
																		.noPassTestCaseId(response.getTestCaseId())
																		.time(response.getTime())
																		.memory(response.getMemory())
																		.resultMessage(Base64.encode("Presentation Error"))
																		.build();
						break;
					}
					// 错误答案 WA
					else {
						judgeResponse = JRBuilder.resultStatus(1005)
																		.passTestCasesNumber(passTestCasesNumber)
																		.noPassTestCaseId(response.getTestCaseId())
																		.time(response.getTime())
																		.memory(response.getMemory())
																		.resultMessage(Base64.encode("Wrong Answer"))
																		.build();
						break;		
					}
				}
				/* 运行时错误 RE */
				else if (exitCode == 1002) {
					judgeResponse = JRBuilder.resultStatus(1002)
																	.passTestCasesNumber(passTestCasesNumber)
																	.noPassTestCaseId(response.getTestCaseId())
																	.resultMessage(Base64.encode(outputMsg))
																	.build();
					break;
				}
				/* 运行超时 TLE */
				else if (exitCode == 1003) {
					judgeResponse = JRBuilder.resultStatus(1003)
																	.passTestCasesNumber(passTestCasesNumber)
																	.noPassTestCaseId(response.getTestCaseId())
																	.time(-1L)
																	.memory(response.getMemory())
																	.resultMessage(Base64.encode("Time Limit Exceeded"))
																	.build();
					break;
				}
				/* 运行时占用内存超出限制 MLE */
				else if (exitCode == 1004) {
					judgeResponse = JRBuilder.resultStatus(1004)
																	.passTestCasesNumber(passTestCasesNumber)
																	.noPassTestCaseId(response.getTestCaseId())
																	.time(response.getTime())
																	.memory(-1L)
																	.resultMessage(Base64.encode("Memory Limit Exceeded"))
																	.build();
					break;
				}
				/* 越权操作 */
				else if (exitCode == 1) {
					judgeResponse = JRBuilder.resultStatus(1)
																	.passTestCasesNumber(passTestCasesNumber)
																	.noPassTestCaseId(response.getTestCaseId())
																	.time(response.getTime())
																	.memory(response.getMemory())
																	.resultMessage(Base64.encode("Permission Deny: " + outputMsg))
																	.build();
					break;
				}
				/* 未知错误 */
				else {
					judgeResponse = JRBuilder.resultStatus(777)
																	.passTestCasesNumber(passTestCasesNumber)
																	.noPassTestCaseId(response.getTestCaseId())
																	.resultMessage(Base64.encode("Unknown Error: ") + outputMsg)
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
	 * @return <用户提交代码存放的目录, 隔离目录名>
	 */
	private static Pair<Path, String> tackleCodeStorageAndIsolation(String code, List<String> inputList, String lang, Long timieLimit, Long memoryLimit) {
		/* 1. 创建代码存放的 "根目录" 的绝对路径 */
		String projectDirPath = System.getProperty("user.dir");
		String codeStoreRootPath = projectDirPath + File.separator + CODE_STORE_ROOT_PATH;

		/* 2. 隔离用户提交的代码文件和测试数据在单独目录 */
		String isolcationDirName = IdUtil.getSnowflakeNextIdStr();
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
			.timeLimit(timieLimit)
			.memoryLimit(memoryLimit)
			.fileDir(VOLUMN_CODE_STORE_ROOT_PATH + File.separator + isolcationDirName + File.separator)
			.testCaseNum(Math.max(1, inputList.size()))
			.lang(langMap.apply(lang))
			.build();
		String jsonString = JSON.toJSONString(requestArgs);
		String jsonFilePath = "request_args.json";
		FileUtil.writeUtf8String(jsonString, userCodeIsolationDirPath + File.separator + jsonFilePath);
		// 封装 file_dir.txt
		FileUtil.writeString(VOLUMN_CODE_STORE_ROOT_PATH + File.separator + isolcationDirName + File.separator,
												 userCodeIsolationDirPath + File.separator + "file-dir.txt", StandardCharsets.UTF_8);
		/* 返回用户提交的代码文件所在的目录 */
		return new Pair<Path,String>(Paths.get(userCodeFile.getParentFile().getAbsolutePath()), isolcationDirName);
	}

	/**
	 * cpp / c / rust / java 代码编译 (python 不需要编译)
	 * 
	 * @param codeFileParentDir java 代码存储路径 (本机)
	 * @param lang 语言
	 * @param containerId 编译容器 Id
	 * @param isolcationDirName c, cpp, rust 代码存储路径 (编译容器)
	 * @return
	 */
	private static ProcessExecuteResult codeCompile(String codeFileParentDir, String lang, String containerId, String isolcationDirName) {
		var messageBuild = ProcessExecuteResult.builder();
		String[] compileCommand = new String[]{};
		// python 不需要编译
		if (lang.equals("python")) return messageBuild.exitValue(0).build();
		if (!lang.equals("java")) codeFileParentDir = VOLUMN_CODE_STORE_ROOT_PATH + File.separator + isolcationDirName;
		/* 匹配对应编程语言的编译命令 */
		compileCommand = CodeLangAdaptUtil.codeCompileCommandArgsAdapt(LangType.getByLangName(lang), codeFileParentDir);
		/* java 在本地编译即可 */
		if (lang.equals("java")) {
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
				log.error("编译失败", e);
			}
		}
		/* c, cpp, rust 在专有镜像中编译 */
		else {
			String[] compileCommandPrefix = new String[] {"docker", "exec", "-i", containerId };
			var processBuilder = new ProcessBuilder(ArrayUtil.append(compileCommandPrefix, compileCommand));
			Process dockerCompilProcess;
			try {
				dockerCompilProcess = processBuilder.start();
				int exitValue = dockerCompilProcess.waitFor();
				ProcessExecuteResult message = new ProcessExecuteResult();
				/* 编译成功 */
				if (exitValue == 0) {
					message = messageBuild.exitValue(0)
						.normalResult(ProcessUtil.getProcessOutput(dockerCompilProcess.getInputStream(), exitValue))
						.build();
				}
				/* 编译失败 */
				else {
					message = messageBuild.exitValue(1001)
						.errorResult(ProcessUtil.getProcessOutput(dockerCompilProcess.getErrorStream(), exitValue))
						.build();
				}
				dockerCompilProcess.destroy();
				return message;
			} catch (IOException | InterruptedException e) {
				throw new BusinessException(StatusEnum.SYSTEM_ERROR, "编译失败. " + e);
			}
		}
		return messageBuild.build();
	}

	/**
	 * 代码运行
	 *
	 * @param containerId       沙箱容器 ID
	 * @param codeFileParentDir 容器挂载目录
	 * @return 运行结果信息
	 */
	private static List<Response> codeRun(String containerId, Path codeFileParentDir) {
		/* 启动 execute_core 执行代码 */
		String[] runCommand = new String[] { "docker", "exec", "-i", containerId, 
          "/execute_core" + File.separator + "execute_core"};
		var processBuilder = new ProcessBuilder(runCommand);
		processBuilder.redirectInput(new File(codeFileParentDir + File.separator + "file-dir.txt"));
		try {
			Process runProcess = processBuilder.start();
			int exitValue = runProcess.waitFor();
			/* execute_core 正常运行 */ 
			if (exitValue == 0) {
				String normalOutput = ProcessUtil.getProcessOutput(runProcess.getInputStream(), exitValue);
				normalOutput = Base64.decodeStr(normalOutput);
				List<Response> execResp = JSONArray.parseArray(normalOutput, Response.class);
				// execResp.forEach(System.out::println);
				return execResp;
			}
			/* execute_core 系统异常 (500 错误) */
			else {
				String errorOutput = ProcessUtil.getProcessOutput(runProcess.getErrorStream(), exitValue);
				var RSBuilder = Response.builder();
				var response = RSBuilder.exitCode(500)
								 .outputMsg(errorOutput)
								 .build();
				log.info("execute_core 出错: \n" + errorOutput);
				return List.of(response);
			}
		} catch (IOException | InterruptedException e) {
			throw new BusinessException(StatusEnum.SYSTEM_ERROR, "运行失败. " + e);
		}
	}
	
	/**
	 * 编译 java 代码的权限校验文件
	 * 
	 * @return 编译后的 .class 文件所在目录
	 */
	@SuppressWarnings("unused")
	@Deprecated
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