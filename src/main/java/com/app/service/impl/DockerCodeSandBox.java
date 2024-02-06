package com.app.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import com.app.module.CodeExecuteResult;
import com.app.module.debug.DebugRequest;
import com.app.module.debug.DebugResponse;
import com.app.module.judge.JudgeRequest;
import com.app.module.judge.JudgeResponse;
import com.app.module.judge.TestCase;
import com.app.service.CodeSandBox;
import com.app.utils.ProcessUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author HDD
 * @date 2024年02月04日
 * @description 使用 Docker 隔离运行环境的代码沙箱
 */
@Component
@Slf4j
@SuppressWarnings({"deprecation"})
public class DockerCodeSandBox implements CodeSandBox {

	private static final String JAVA_DOCKER_IMAGE = "openjdk:17.0-jdk";
	private static final String JAVA_CONTAINER_NAME = "jdk17_environment";
	private static final String CODE_STORE_ROOT_PATH = "tempCodeRepository";
	private static final String EXECUTE_CODE_FILE_NAME = "Main.java";
	private static final String[] JAVA_COMPILE_COMMAND = new String[]{"javac", "-encoding", "utf-8"};
	private static final String INPUT_NAME_PREFIX = "input-";
	private static final Long TIME_LIMIT = 3000L;  // 3s
	private static final Long Memory_LIMIT = 256 * 1000 * 1000L; // 256MB

	/**
	 * 代码调试
	 *
	 * @param debugRequest 代码调试请求
	 * @return 代码调试结果
	 */
	@Override
	public DebugResponse codeDebug(DebugRequest debugRequest) {
		var respBuilder = DebugResponse.builder();
		String code = debugRequest.getCode();
		String input = debugRequest.getInput();
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
					.resultMessage("Include Malicious Code")
					.build();
		}
		/* 代码编译 */
		var codeCompileResult = codeCompile(codeFileParentDir.toString());
		// 编译失败 (Compiler Error)
		if (codeCompileResult.getExitValue() != 0) {
			codeFileClean(codeFileParentDir.toString());
			return respBuilder.resultStatus(2)
					.resultMessage(codeCompileResult.getErrorResult())
					.build();
		}

		var dockerClient = DockerClientBuilder.getInstance().build();
		String containerId = getContainerId(dockerClient, codeFileParentDir);
		var containerInfo = dockerClient.inspectContainerCmd(containerId).exec();
		if (Boolean.FALSE.equals(containerInfo.getState().getRunning())) {
			dockerClient.startContainerCmd(containerId).exec(); // 启动容器
		}

		var codeExecuteResults = DockerCodeSandBox.codeRun(dockerClient, containerId, codeFileParentDir, inputList, TIME_LIMIT, Memory_LIMIT);
		var codeRunResult = codeExecuteResults.get(0);
		// AC
		if (codeRunResult.getExitValue() == 0) {
			codeFileClean(codeFileParentDir.toString());
			return respBuilder.resultStatus(0)
					.resultMessage(codeRunResult.getNormalResult())
					.time(codeRunResult.getTime())
					.memory(codeRunResult.getMemory())
					.build();
		}
		// 运行超时 (Run Timeout)
		else if (codeRunResult.getExitValue() == 4) {
			codeFileClean(codeFileParentDir.toString());
			return respBuilder.resultStatus(4)
					.resultMessage(codeRunResult.getErrorResult())
					.time(Long.MAX_VALUE)
					.build();
		} else {
			// 权限不足
			codeFileClean(codeFileParentDir.toString());
			String[] permissionException = codeRunResult.getErrorResult().split("#");
			return respBuilder.resultStatus(1)
					.resultMessage(permissionException[1])
					.build();
		}
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
		String code = judgeRequest.getCode();
		List<String> inputList = judgeRequest.getTestCases().stream().map(TestCase::getInput).toList();
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

		var dockerClient = DockerClientBuilder.getInstance().build();
		String containerId = getContainerId(dockerClient, codeFileParentDir);
		var containerInfo = dockerClient.inspectContainerCmd(containerId).exec();
		if (Boolean.FALSE.equals(containerInfo.getState().getRunning())) {
			dockerClient.startContainerCmd(containerId).exec(); // 启动容器
		}
		/* 代码运行 */
		var codeRunResults = DockerCodeSandBox.codeRun(dockerClient, containerId, codeFileParentDir, inputList, judgeRequest.getTimeLimit(), judgeRequest.getMemoryLimit());

		var judgeResponse = new JudgeResponse();
		int passTestCasesNumber = 0;
		long time = 0L;
		long memory = 0L;
		for (CodeExecuteResult res : codeRunResults) {
			/* 正常运行 */
			if (res.getExitValue() == 0) {
				// 当前测试数据通过
				if (Objects.equals(res.getNormalResult(), mp.get(res.getTestCaseId()))) {
					passTestCasesNumber++;
					judgeResponse = JRBuilder.passTestCasesNumber(passTestCasesNumber)
							.time(Math.max(time, res.getTime()))
							.memory(Math.max(memory, res.getMemory()))
							.build();
				}
				// 当前测试数据未通过 —— 运行失败 (Runtime Error)
				else {
					judgeResponse = JRBuilder.resultStatus(3)
							.passTestCasesNumber(passTestCasesNumber)
							.resultMessage("Runtime Error")
							.noPassTestCaseId(res.getTestCaseId())
							.time(Math.max(time, res.getTime()))
							.memory(Math.max(memory, res.getMemory()))
							.build();
				}
			}
			/* Runtime Timeout */
			else if (res.getExitValue() == 4) {
				judgeResponse = JRBuilder.resultStatus(4)
						.passTestCasesNumber(passTestCasesNumber)
						.resultMessage("Runtime Timeout")
						.noPassTestCaseId(res.getTestCaseId())
						.time(Long.MAX_VALUE)
						.memory(Math.max(memory, res.getMemory()))
						.build();
				codeFileClean(codeFileParentDir.toString());
				return judgeResponse;   // 有一个超时, 直接返回
			}
			/* Permission Deny */
			else {
				judgeResponse = JRBuilder.resultStatus(1)
						.resultMessage("Permission Deny")
						.build();
				// todo
				codeFileClean(codeFileParentDir.toString());
			}
		}
		// AC ——通过测试数据数 == 测试数据数
		if (passTestCasesNumber == codeRunResults.size()) {
			judgeResponse.setResultStatus(0);
			judgeResponse.setResultMessage("AC");
			judgeResponse.setNoPassTestCaseId(-1);
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
		String[] compileCommand = ArrayUtil.append(JAVA_COMPILE_COMMAND, codeFileParentDir + File.separator + EXECUTE_CODE_FILE_NAME);
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
		hostConfig.withMemory(512 * 1024 * 1024L);
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
		var listContainersCmd = dockerClient.listContainersCmd().withNameFilter(List.of(JAVA_CONTAINER_NAME));
		var listImageCmd = dockerClient.listImagesCmd().withReferenceFilter(JAVA_DOCKER_IMAGE);
		List<Container> existedContainer = listContainersCmd.exec();
		List<Image> existedImage = listImageCmd.exec();

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
	 * @param memory            内存限制
	 * @return List<CodeExecuteResult> 运行结果信息
	 */
	private static List<CodeExecuteResult> codeRun(DockerClient dockerClient, String containerId, Path codeFileParentDir, List<String> inputList, Long timeLimit, Long memory) {
		List<CodeExecuteResult> messages = new ArrayList<>();
		String permissionCheckFilePath = System.getProperty("user.dir") + File.separator + "tempCodeRepository" + File.separator + "DenySecurity.class";
		if (!Files.exists(Paths.get(permissionCheckFilePath))) {
			compileDenyPermissionFile();
		}

		final long[] maxMemory = { 0L };
		StatsCmd statsCmd = dockerClient.statsCmd(containerId);
		ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
			@Override
			public void onNext(Statistics statistics) {
				System.out.println("内存占用: " + statistics.getMemoryStats().getUsage());
				maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
			}
			@Override
			public void close() {}
			@Override
			public void onStart(Closeable closeable) {}
			@Override
			public void onError(Throwable throwable) {}
			@Override
			public void onComplete() {}
		});
		statsCmd.exec(statisticsResultCallback);

		ExecutorService executor = Executors.newFixedThreadPool(4);
		List<Callable<CodeExecuteResult>> tasks = new ArrayList<>();

		for (int i = 0; i < inputList.size(); i++) {
			int index = i;
			tasks.add(() -> {
				String[] runCommand = new String[]{"docker", "exec", "-i", containerId,
						"java","-Xmx256m", "-cp", "/codeStore" + File.separator + codeFileParentDir.getFileName().toString()+
						":"+"/codeStore","-Djava.security.manager=DenyPermission", "Main"};


				var processBuilder = new ProcessBuilder(runCommand);
				if (!inputList.get(index).trim().isEmpty()) {
					processBuilder.redirectInput(new File(codeFileParentDir + File.separator + INPUT_NAME_PREFIX + index + ".txt"));
				}
				var stopWatch = new StopWatch();

				stopWatch.start(); // ----------------------------- 开始统计进程运行时间
				Process runProcess = processBuilder.start();

				Integer exitValue;
				var messageBuilder = CodeExecuteResult.builder();
				var message = new CodeExecuteResult();

				if (runProcess.waitFor(timeLimit, TimeUnit.MILLISECONDS)) {
					exitValue = runProcess.exitValue();
					stopWatch.stop(); // ----------------------------- 结束统计进程运行时间


					message = messageBuilder.exitValue(exitValue)
							.testCaseId(index)
							.time(stopWatch.getLastTaskTimeMillis())
							.memory(maxMemory[0])
							.normalResult(ProcessUtil.getProcessOutput(runProcess.getInputStream(), exitValue))
							.errorResult(ProcessUtil.getProcessOutput(runProcess.getErrorStream(), exitValue))
							.build();
				} else {
					stopWatch.stop(); // ----------------------------- 结束统计进程运行时间
					exitValue = 4;    // Runtime Timeout
					message = messageBuilder.exitValue(exitValue)
							.testCaseId(index)
							.memory(maxMemory[0])
							.time(stopWatch.getLastTaskTimeMillis())
							.errorResult("Runtime Timeout")
							.build();
					runProcess.destroy();
				}
				statsCmd.close();
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
		statsCmd.close();
		return messages;
	}

	/**
	 * 编译权限校验文件
	 * @return 编译后的 .class 文件所在目录
	 */
	private static String compileDenyPermissionFile() {
		String userDir = System.getProperty("user.dir");
		String classPath = userDir + File.separator + "tempCodeRepository";
		String javaPath = userDir + File.separator +"src/main/resources/permission/DenyPermission.java";
		var processBuilder = new ProcessBuilder(new String[]{"javac", "-d",classPath , javaPath});
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