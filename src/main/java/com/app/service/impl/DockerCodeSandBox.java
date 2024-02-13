package com.app.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import com.app.module.CodeExecuteResult;
import com.app.module.debug.DebugRequest;
import com.app.module.debug.DebugResponse;
import com.app.module.judge.JudgeRequest;
import com.app.module.judge.JudgeResponse;
import com.app.service.CodeSandBox;
import com.app.utils.ProcessUtil;
import com.app.utils.ProcessUtil.MemoryMonitor;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import lombok.var;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * @author HDD
 * @date 2024年02月04日
 * @description 使用 Docker 隔离运行环境的代码沙箱
 */
@Component
@Slf4j
@SuppressWarnings({ "deprecation" })
public class DockerCodeSandBox implements CodeSandBox {

	private static final String JAVA_DOCKER_IMAGE = "openjdk:17.0-jdk";
	private static final String JAVA_CONTAINER_NAME = "jdk17_environment";
	private static final String CODE_STORE_ROOT_PATH = "tempCodeRepository";
	private static final String EXECUTE_CODE_FILE_NAME = "Main.java";
	private static final String[] JAVA_COMPILE_COMMAND = new String[] { "javac", "-encoding", "utf-8" };
	private static final String INPUT_NAME_PREFIX = "input-";
	/* 代码调试限制 (相对宽松)  */ 
	private static final Long TIME_LIMIT = 2000L; // 2s
	private static final Long Memory_LIMIT = 256 * 1000 * 1000L; // 256MB

	/**
	 * 代码调试
	 * 
	 * @param debugRequest 代码调试请求
	 * @return 代码调试结果 
	 */
	@SuppressWarnings("null")
	@Override
	public DebugResponse codeDebug(DebugRequest debugRequest) {
		var respBuilder = DebugResponse.builder();
		String code = Base64.decodeStr(debugRequest.getCode());
		String input = Base64.decodeStr(debugRequest.getInput());
		List<String> inputList = new ArrayList<>();
		if (input != null) {
			inputList.add(input.trim());
		} else {
			inputList.add("");
		}
		Path codeFileParentDir = tackleCodeStorageAndIsolation(code, inputList);

		/* 类库黑名单校验 */
		Boolean isMaliciousCode = IsMaliciousCode(code);
		// 含有危险代码 (Include Malicious Code)
		if (isMaliciousCode) {
			codeFileClean(codeFileParentDir.toString());
			return respBuilder.resultStatus(1)
					.resultMessage(Base64.encode("Include Malicious Code"))
					.build();
		}
		/* 代码编译 */
		var codeCompileResult = codeCompile(codeFileParentDir.toString());
		// 编译失败 (Compiler Error)
		if (codeCompileResult.getExitValue() != 0) {
			codeFileClean(codeFileParentDir.toString());
			Function<String, String> tackleOutput = (String errorResult) -> {
				String[] compileError = errorResult.split("Main.java");
				if (compileError.length >= 2) {
					return "Main.java: " + compileError[1];
				}
				return errorResult;
			};
			return respBuilder.resultStatus(2)
					.resultMessage(Base64.encode(tackleOutput.apply(codeCompileResult.getErrorResult())))
					.build();
		}

    DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
		DockerHttpClient dockerHttpClient = new ApacheDockerHttpClient.Builder()
																	.dockerHost(config.getDockerHost())
																	.sslConfig(config.getSSLConfig())
																	.maxConnections(1000)
																	.build();
		var dockerClient = DockerClientBuilder.getInstance(config).withDockerHttpClient(dockerHttpClient).build();
		String containerId = getContainerId(dockerClient, codeFileParentDir);
		var containerInfo = dockerClient.inspectContainerCmd(containerId).exec();
		if (Boolean.FALSE.equals(containerInfo.getState().getRunning())) {
			dockerClient.startContainerCmd(containerId).exec(); // 启动容器
		}

		/* 代码运行 */
		var codeExecuteResults = DockerCodeSandBox.codeRun(dockerClient, containerId, codeFileParentDir, inputList,
				TIME_LIMIT, Memory_LIMIT);
		var codeRunResult = codeExecuteResults.get(0);
		// 代码正常运行
		if (codeRunResult.getExitValue() == 0) {
			codeFileClean(codeFileParentDir.toString());
			return respBuilder.resultStatus(0)
					.resultMessage(Base64.encode(codeRunResult.getNormalResult()))
					.time(codeRunResult.getTime())
					.memory(codeRunResult.getMemory())
					.build();
		}
		// 运行超时 (Runtime Timeout)
		else if (codeRunResult.getExitValue() == 4) {
			codeFileClean(codeFileParentDir.toString());
			return respBuilder.resultStatus(4)
					.resultMessage(Base64.encode(codeRunResult.getErrorResult()))
					.time(-1L)
					.build();
		}
		// 内存溢出 (Runtime Out Of Memory)
		else if (codeRunResult.getExitValue() == 5) {
			codeFileClean(codeFileParentDir.toString());
			return respBuilder.resultStatus(5)
					.resultMessage(Base64.encode(codeRunResult.getErrorResult()))
					.memory(-1L)
					.build();
		}
		// 运行时错误 (Runtime Error)
		else {
			codeFileClean(codeFileParentDir.toString());

			Function<String, Object> isPermissionDenyInfo = (String errorResult) -> {
				String[] permissionException = errorResult.split("#as#");
				if (permissionException.length >= 2) {
					return permissionException[1];
				}
				return null;
			};
			var permissionMessage = isPermissionDenyInfo.apply(codeRunResult.getErrorResult());
			/* 越权操作 (Permission Deny) */
			if (permissionMessage != null) {
				return respBuilder.resultStatus(3)
					.resultMessage(Base64.encode(permissionMessage.toString()))
					.build();
			}
			/* 其他运行时错误 */
			Function<String, String> tackleRuntimeErrorOutput = (String errorResult) -> {
				String[] runtimeException = errorResult.split("future release");
				if (runtimeException.length >= 2) {
					return runtimeException[1];
				}
				return errorResult;				
			};
			return respBuilder.resultStatus(3)
					.resultMessage(Base64.encode(tackleRuntimeErrorOutput.apply(codeRunResult.getErrorResult())))
					.build();
		}
	}

	/**
	 * 代码评审
	 *
	 * @param judgeRequest 代码评审请求
	 * @return 代码评审结果
	 */
	@SuppressWarnings("null")
	@Override
	public JudgeResponse codeJudge(JudgeRequest judgeRequest) {
		var JRBuilder = JudgeResponse.builder();
		String code = Base64.decodeStr(judgeRequest.getCode());
		List<String> inputList = judgeRequest.getTestCases().stream().map(e -> {
			return Base64.decodeStr(e.getInput());
		}).toList();
		Path codeFileParentDir = tackleCodeStorageAndIsolation(code, inputList);
		/* 类库黑名单校验 */
		Boolean isMaliciousCode = IsMaliciousCode(code);
		// 含有危险代码 (Include Malicious Code)
		if (isMaliciousCode) {
			codeFileClean(codeFileParentDir.toString());
			return JRBuilder.resultStatus(1)
					.resultMessage("Include Malicious Code")
					.build();
		}
		/* 代码编译 */
		var codeCompileResult = codeCompile(codeFileParentDir.toString());
		// 编译失败 (Compiler Error)
		if (codeCompileResult.getExitValue() != 0) {
			codeFileClean(codeFileParentDir.toString());
			return JRBuilder.resultStatus(2)
					.resultMessage(codeCompileResult.getErrorResult())
					.build();
		}

		// 构建测试数据的ID 和 其正确结果之间的 HashMap
		HashMap<Integer, String> mp = new HashMap<>();
		judgeRequest.getTestCases().forEach(testCase -> mp.put(testCase.getId(), testCase.getCorrectResult()));

    DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
		DockerHttpClient dockerHttpClient = new ApacheDockerHttpClient.Builder()
																	.dockerHost(config.getDockerHost())
																	.sslConfig(config.getSSLConfig())
																	.maxConnections(100)
																	.build();
		var dockerClient = DockerClientBuilder.getInstance(config).withDockerHttpClient(dockerHttpClient).build();
		String containerId = getContainerId(dockerClient, codeFileParentDir);
		var containerInfo = dockerClient.inspectContainerCmd(containerId).exec();
		if (Boolean.FALSE.equals(containerInfo.getState().getRunning())) {
			dockerClient.startContainerCmd(containerId).exec(); // 启动容器
		}
		/* 代码运行 */
		var codeRunResults = DockerCodeSandBox.codeRun(dockerClient, containerId, codeFileParentDir, inputList,
				judgeRequest.getTimeLimit(), judgeRequest.getMemoryLimit());

		var judgeResponse = new JudgeResponse();
		int passTestCasesNumber = 0;
		long time = 0L;
		long memory = 0L;
		for (CodeExecuteResult res : codeRunResults) {
			// 代码正常运行
			if (res.getExitValue() == 0) {
				// 当前测试数据通过
				if (Objects.equals(res.getNormalResult(), mp.get(res.getTestCaseId()))) {
					passTestCasesNumber++;
					time += (res.getTime() / 2);
					memory += res.getMemory();
				}
				// 当前测试数据未通过 —— 答案错误 (Wrong Answer)
				else {
					judgeResponse = JRBuilder.resultStatus(6)
							.passTestCasesNumber(passTestCasesNumber)
							.resultMessage("Wrong Answer")
							.noPassTestCaseId(res.getTestCaseId())
							.time(Math.max(time, res.getTime()))
							.memory(Math.max(memory, res.getMemory()))
							.build();
					return judgeResponse; // 有一个错误,直接返回
				}
			}
			// 运行超时 (Runtime Timeout)
			else if (res.getExitValue() == 4) {
				judgeResponse = JRBuilder.resultStatus(4)
						.passTestCasesNumber(passTestCasesNumber)
						.resultMessage("Runtime Timeout")
						.noPassTestCaseId(res.getTestCaseId())
						.time(-1L)
						.memory(Math.max(memory, res.getMemory()))
						.build();
				codeFileClean(codeFileParentDir.toString());
				return judgeResponse; // 有一个超时, 直接返回
			}
			// 内存溢出 (Runtime Out Of Memory)
			else if (res.getExitValue() == 5) {
				judgeResponse = JRBuilder.resultStatus(5)
						.passTestCasesNumber(passTestCasesNumber)
						.resultMessage(Base64.encode(res.getErrorResult()))
						.noPassTestCaseId(res.getTestCaseId())
						.memory(-1L)
						.build();
				return judgeResponse; // 有一个内存溢出, 直接返回
			}
			// 运行时错误 (Runtime Error)
			else {
				codeFileClean(codeFileParentDir.toString());
				Function<String, Object> isPermissionDenyInfo = (String errorResult) -> {
					String[] permissionException = errorResult.split("#as#");
					if (permissionException.length >= 2) {
						return permissionException[1];
					}
					return null;
				};
				var permissionMessage = isPermissionDenyInfo.apply(res.getErrorResult());
				/* 越权操作 (Permission Deny) */
				if (permissionMessage != null) {
					return JRBuilder.resultStatus(3)
						.resultMessage(Base64.encode(permissionMessage.toString()))
						.build();
				}
				/* 其他运行时错误 */
				Function<String, String> tackleRuntimeErrorOutput = (String errorResult) -> {
					String[] runtimeException = errorResult.split("release");
					if (runtimeException.length >= 2) {
						return runtimeException[1];
					}
					return errorResult;				
				};
				return JRBuilder.resultStatus(3)
						.resultMessage(Base64.encode(tackleRuntimeErrorOutput.apply(res.getErrorResult())))
						.build();
			}
		}
		// AC —— 通过测试数据数 == 测试数据数
		if (passTestCasesNumber == codeRunResults.size()) {
			judgeResponse = JRBuilder.resultStatus(0)
					.resultMessage("AC")
					.time(time)
					.memory(memory)
					.noPassTestCaseId(-1)
					.passTestCasesNumber(passTestCasesNumber)
					.build();
		}
		codeFileClean(codeFileParentDir.toString());
		return judgeResponse;
	}

	/**
	 * 恶意代码分析 (类库黑名单)
	 *
	 * @return 是否含有恶意代码
	 */
	private static Boolean IsMaliciousCode(String code) {
		// todo 黑名单校验
		return false;
	}

	/**
	 * 处理用户代码的存储隔离
	 *
	 * @param code 用户提交的代码
	 * @return 用户提交代码存放的目录
	 */
	private static Path tackleCodeStorageAndIsolation(String code, List<String> inputList) {
		/* 创建代码存放目录 */
		String userDir = System.getProperty("user.dir");
		String codeStoreRootPath = userDir + File.separator + CODE_STORE_ROOT_PATH;
		if (!FileUtil.exist(codeStoreRootPath)) {
			FileUtil.mkdir(codeStoreRootPath);
		}

		/* 隔离用户提交的代码文件 */
		String userCodeRootPath = codeStoreRootPath + File.separator + UUID.randomUUID();
		String userCodePath = userCodeRootPath + File.separator + EXECUTE_CODE_FILE_NAME;
		File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
		for (int i = 0; i < inputList.size(); i++) {
			String inputFilePath = userCodeRootPath + File.separator + INPUT_NAME_PREFIX + i + ".txt";
			FileUtil.writeString(inputList.get(i).trim(), inputFilePath, StandardCharsets.UTF_8);
		}
		/* 返回用户提交的代码文件所在的目录 */
		return Paths.get(userCodeFile.getParentFile().getAbsolutePath());
	}

	/**
	 * java 代码编译
	 *
	 * @param codeFileParentDir 待编译 Main.java 文件存储目录
	 * @return CodeExecuteResult 编译结果信息
	 */
	private static CodeExecuteResult codeCompile(String codeFileParentDir) {
		var messageBuild = CodeExecuteResult.builder();
		String[] compileCommand = ArrayUtil.append(JAVA_COMPILE_COMMAND,
				codeFileParentDir + File.separator + EXECUTE_CODE_FILE_NAME);
		var processBuilder = new ProcessBuilder(compileCommand);
		try {
			Process compileProcess = processBuilder.start();
			int exitValue = compileProcess.waitFor();
			var message = messageBuild.exitValue(exitValue)
					.normalResult(ProcessUtil.getProcessOutput(compileProcess.getInputStream(), exitValue))
					.errorResult(ProcessUtil.getProcessOutput(compileProcess.getErrorStream(), exitValue))
					.build();
			compileProcess.destroy();
			return message;
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
		return messageBuild.build();
	}

	/**
	 * 拉取 jdk 17 的环境镜像
	 *
	 * @param dockerClient docker 客户端
	 */
	private static void pullJDKImage(DockerClient dockerClient) {
		var pullImageCmd = dockerClient.pullImageCmd(JAVA_DOCKER_IMAGE);
		var pullImageResultCallback = new PullImageResultCallback() {
			@Override
			public void onNext(PullResponseItem item) {
				log.info("下载镜像中: " + item.getStatus());
				super.onNext(item);
			}
		};
		try {
			pullImageCmd.exec(pullImageResultCallback)
					.awaitCompletion();
		} catch (InterruptedException e) {
			log.error("拉取镜像失败");
			e.printStackTrace();
		}
	}

	/**
	 * 利用 jdk17 镜像创建容器
	 *
	 * @param codeFileParentDir 要挂载在容器 /codeStore 下的目录
	 * @param dockerClient      docker 客户端
	 * @return 已创建的容器的 ID
	 */
	private static String createContainer(Path codeFileParentDir, DockerClient dockerClient) {
		var containerCmd = dockerClient.createContainerCmd(JAVA_DOCKER_IMAGE).withName(JAVA_CONTAINER_NAME);
		var hostConfig = new HostConfig();
		log.info("挂载目录:" + codeFileParentDir.getParent().toString());
		hostConfig.setBinds(new Bind(codeFileParentDir.getParent().toString(), new Volume("/codeStore")));
		hostConfig.withMemory(384 * 1024 * 1024L);
		hostConfig.withCpuCount(1L);
		var containerInstance = containerCmd
				.withHostConfig(hostConfig)
				.withNetworkDisabled(true)
				.withReadonlyRootfs(true)
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
		var listContainersCmd = dockerClient.listContainersCmd().withNameFilter(List.of(JAVA_CONTAINER_NAME))
				.withShowAll(true);
		var listImageCmd = dockerClient.listImagesCmd().withReferenceFilter(JAVA_DOCKER_IMAGE);

		List<Image> existedImage = listImageCmd.exec();
		List<Container> existedContainer = listContainersCmd.exec();

		String containerId;
		if (existedImage.isEmpty() && existedContainer.isEmpty()) {
			pullJDKImage(dockerClient);
			containerId = createContainer(codeFileParentDir, dockerClient);
		} else if (!existedImage.isEmpty() && existedContainer.isEmpty()) {
			containerId = createContainer(codeFileParentDir, dockerClient);
		} else {
			containerId = existedContainer.get(0).getId();
		}
		return containerId;
	}

	/**
	 * java 代码运行
	 *
	 * @param dockerClient      docker 客户端
	 * @param containerId       jdk17容器 ID
	 * @param codeFileParentDir 容器挂载目录
	 * @param inputList         测试数据
	 * @param timeLimit         时间限制
	 * @param memoryLimit       内存限制
	 * @return List<CodeExecuteResult> 运行结果信息
	 */
	private static List<CodeExecuteResult> codeRun(DockerClient dockerClient, String containerId, Path codeFileParentDir,
			List<String> inputList, Long timeLimit, Long memoryLimit) {
		List<CodeExecuteResult> messages = new ArrayList<>();
		String permissionCheckFilePath = System.getProperty("user.dir") + File.separator + "tempCodeRepository"
				+ File.separator + "DenySecurity.class";
		if (!Files.exists(Paths.get(permissionCheckFilePath))) {
			compileDenyPermissionFile();
		}

		ExecutorService executor = Executors.newFixedThreadPool(4);
		List<Callable<CodeExecuteResult>> tasks = new ArrayList<>();



		Long memoryLimitMB = memoryLimit / (1024 * 1024);
		for (int i = 0; i < inputList.size(); i++) {
			int index = i;
			tasks.add(() -> {
				String[] runCommand = new String[] { "docker", "exec", "-i", containerId,
						"java", "-Xmx" + memoryLimitMB + "m", "-cp",
						"/codeStore" + File.separator + codeFileParentDir.getFileName().toString() +
								":" + "/codeStore",
						"-Djava.security.manager=DenyPermission", "Main" };

				var processBuilder = new ProcessBuilder(runCommand);
				if (!inputList.get(index).trim().isEmpty()) {
					processBuilder
							.redirectInput(new File(codeFileParentDir + File.separator + INPUT_NAME_PREFIX + index + ".txt"));
				}
				var stopWatch = new StopWatch();

				Process runProcess = processBuilder.start();
				stopWatch.start(); // ----------------------------- 开始统计进程运行时间

				/* 启动内存监控守护进程 */
				MemoryMonitor memoryMonitor = new ProcessUtil.MemoryMonitor(runProcess);
				Thread monitorThread = new Thread(memoryMonitor);
				monitorThread.setDaemon(true);
				monitorThread.start();

				Integer exitValue;
				var messageBuilder = CodeExecuteResult.builder();
				var message = new CodeExecuteResult();

				if (runProcess.waitFor(timeLimit, TimeUnit.MILLISECONDS)) {
					exitValue = runProcess.exitValue();
					stopWatch.stop(); // ----------------------------- 结束统计进程运行时间

					String errorMessage = ProcessUtil.getProcessOutput(runProcess.getErrorStream(), exitValue);
					// Runtime Out Of Memory
					if (errorMessage.contains("java.lang.OutOfMemoryError")) {
						exitValue = 5;
						errorMessage = errorMessage.split("release")[1];
					}

					message = messageBuilder.exitValue(exitValue)
							.testCaseId(index)
							.time(stopWatch.getLastTaskTimeMillis())
							.memory(memoryMonitor.getPeakMemoryUsage())
							.normalResult(ProcessUtil.getProcessOutput(runProcess.getInputStream(), exitValue))
							.errorResult(errorMessage)
							.build();
				} else {
					stopWatch.stop(); // ----------------------------- 结束统计进程运行时间
					exitValue = 4; // Runtime Timeout
					message = messageBuilder.exitValue(exitValue)
							.testCaseId(index)
							.memory(memoryMonitor.getPeakMemoryUsage())
							.time(-1L)
							.errorResult("Runtime Timeout")
							.build();
					runProcess.destroy();
				}
				return message;
			});
		}

		try {
			List<Future<CodeExecuteResult>> futures = executor.invokeAll(tasks, timeLimit + 30, TimeUnit.MILLISECONDS);
			for (Future<CodeExecuteResult> future : futures) {
				try {
					if (!future.isCancelled()) {
						CodeExecuteResult result = future.get();
						messages.add(result);
					}
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			executor.shutdownNow();
		}
		return messages;
	}


	/**
	 * 编译权限校验文件
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