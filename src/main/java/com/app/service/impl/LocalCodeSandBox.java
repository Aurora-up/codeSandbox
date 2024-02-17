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
 * @description 本地代码沙箱
 */
@Component
public class LocalCodeSandBox implements CodeSandBox {
	private static final String CODE_STORE_ROOT_PATH = "tempCodeRepository";
	private static final String EXECUTE_CODE_FILE_NAME = "Main.java";
	private static final String[] JAVA_COMPILE_COMMAND = new String[] { "javac", "-encoding", "utf-8" };
	private static final String[] JAVA_RUN_COMMAND = new String[] { "java", "-Xmx256m", "-Dfile.encoding=UTF-8", "-cp" };
	private static final String INPUT_NAME_PREFIX = "input-";
	private static final Long TIME_LIMIT = 3000L; // 3s
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
		String codeFileParentDir = tackleCodeStorageAndIsolation(code, inputList);

		/* 类库黑名单校验 */
		Boolean isMaliciousCode = IsMaliciousCode(code);
		// 含有危险代码 (Include Malicious Code)
		if (isMaliciousCode) {
			codeFileClean(codeFileParentDir);
			return respBuilder.resultStatus(1)
					.resultMessage("Include Malicious Code")
					.build();
		}
		/* 代码编译 */
		var codeCompileResult = codeCompile(codeFileParentDir);
		// 编译失败 (Compiler Error)
		if (codeCompileResult.getExitValue() != 0) {
			codeFileClean(codeFileParentDir);
			return respBuilder.resultStatus(2)
					.resultMessage(codeCompileResult.getErrorResult())
					.build();
		}
		/* 代码运行 */
		var codeExecuteResults = codeRun(codeFileParentDir, inputList, TIME_LIMIT, Memory_LIMIT);
		var codeRunResult = codeExecuteResults.get(0);
		// 代码正常运行
		if (codeRunResult.getExitValue() == 0) {
			codeFileClean(codeFileParentDir);
			return respBuilder.resultStatus(0)
					.resultMessage(codeRunResult.getNormalResult() + codeRunResult.getErrorResult())
					.time(codeRunResult.getTime())
					.build();
		}
		// 运行超时 (Runtime Timeout)
		else if (codeRunResult.getExitValue() == 4) {
			codeFileClean(codeFileParentDir);
			return respBuilder.resultStatus(4)
					.resultMessage(codeRunResult.getErrorResult())
					.time(Long.MAX_VALUE)
					.build();
		}
		// 内存溢出 (Runtime Out Of Memory)
		else if (codeRunResult.getExitValue() == 5) {
			codeFileClean(codeFileParentDir);
			return respBuilder.resultStatus(5)
					.resultMessage(codeRunResult.getErrorResult())
					.build();
		}
		// 运行时错误——越权操作 (Runtime Error —— Permission Deny)
		else {
			codeFileClean(codeFileParentDir);
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
					.resultMessage("Runtime Error: " + permissionMessage.toString())
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
					.resultMessage("Runtime Error: " + tackleRuntimeErrorOutput.apply(codeRunResult.getErrorResult()))
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
		String codeFileParentDir = tackleCodeStorageAndIsolation(code, inputList);
		/* 类库黑名单校验 */
		Boolean isMaliciousCode = IsMaliciousCode(code);
		// 含有危险代码 (Include Malicious Code)
		if (isMaliciousCode) {
			codeFileClean(codeFileParentDir);
			return JRBuilder.resultStatus(1)
					.resultMessage("Include Malicious Code")
					.build();
		}
		/* 代码编译 */
		var codeCompileResult = codeCompile(codeFileParentDir);
		// 编译失败 (Compiler Error)
		if (codeCompileResult.getExitValue() != 0) {
			codeFileClean(codeFileParentDir);
			return JRBuilder.resultStatus(2)
					.resultMessage(codeCompileResult.getErrorResult())
					.build();
		}
		// 构建测试数据的ID 和 其正确结果之间的 HashMap
		HashMap<Integer, String> mp = new HashMap<>();
		judgeRequest.getTestCases().forEach(testCase -> mp.put(testCase.getId(), testCase.getCorrectResult()));

		/* 代码运行 */
		var codeRunResults = codeRun(codeFileParentDir, inputList, judgeRequest.getTimeLimit(),
				judgeRequest.getMemoryLimit());
		var judgeResponse = new JudgeResponse();
		int passTestCasesNumber = 0;
		Long time = 0L;

		for (CodeExecuteResult res : codeRunResults) {
			// 代码正常运行
			if (res.getExitValue() == 0) {
				// 当前测试数据通过
				if (Objects.equals(res.getNormalResult(), mp.get(res.getTestCaseId()))) {
					passTestCasesNumber++;
					judgeResponse = JRBuilder.passTestCasesNumber(passTestCasesNumber)
							.time(Math.max(time, res.getTime()))
							.build();
				}
				// 当前测试数据未通过 —— 答案错误 (Wrong Answer)
				else {
					judgeResponse = JRBuilder.resultStatus(6)
							.passTestCasesNumber(passTestCasesNumber)
							.resultMessage("Wrong Answer")
							.noPassTestCaseId(res.getTestCaseId())
							.build();
					return judgeResponse;
				}
			}
			// 运行超时 (Runtime Timeout)
			else if (res.getExitValue() == 4) {
				judgeResponse = JRBuilder.resultStatus(4)
						.passTestCasesNumber(passTestCasesNumber)
						.resultMessage("Runtime Timeout")
						.noPassTestCaseId(res.getTestCaseId())
						.time(Long.MAX_VALUE)
						.build();
				codeFileClean(codeFileParentDir);
				return judgeResponse; // 有一个超时, 直接返回
			}
			// 内存溢出 (Runtime Out Of Memory)
			else if (res.getExitValue() == 5) {
				judgeResponse = JRBuilder.resultStatus(5)
						.passTestCasesNumber(passTestCasesNumber)
						.resultMessage(res.getErrorResult())
						.noPassTestCaseId(res.getTestCaseId())
						.memory(Long.MAX_VALUE)
						.build();
				return judgeResponse; // 有一个内存溢出, 直接返回
			}
			// 运行时错误——越权操作 (Runtime Error —— Permission Deny)
			else {
				codeFileClean(codeFileParentDir);
				String[] permissionException = res.getErrorResult().split("#");
				judgeResponse = JRBuilder.resultStatus(3)
						.passTestCasesNumber(passTestCasesNumber)
						.resultMessage("Runtime Error —— " + permissionException[1])
						.noPassTestCaseId(res.getTestCaseId())
						.build();
				return judgeResponse; // 有一个越权, 直接返回
			}
		}
		// AC ——通过测试数据数 == 测试数据数
		if (passTestCasesNumber == codeRunResults.size()) {
			judgeResponse.setResultStatus(0);
			judgeResponse.setResultMessage("AC");
			judgeResponse.setNoPassTestCaseId(-1);
		}
		codeFileClean(codeFileParentDir);
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
	private static String tackleCodeStorageAndIsolation(String code, List<String> inputList) {
		/* 创建代码存放目录 */
		String userDir = System.getProperty("user.dir");
		String codeStoreRootPath = userDir + File.separator + CODE_STORE_ROOT_PATH;
		if (!FileUtil.exist(codeStoreRootPath)) {
			FileUtil.mkdir(codeStoreRootPath);
		}

		/* 隔离用户提交的代码文件 */
		String userCodeRootPath = codeStoreRootPath + File.separator + UUID.randomUUID(); // 构造唯一目录名
		String userCodePath = userCodeRootPath + File.separator + EXECUTE_CODE_FILE_NAME; // 该目录下创建 Main.java 文件
		File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8); // 将用户代码写入 Main.java 中
		if (inputList.size() != 0 || inputList != null) {
			for (int i = 0; i < inputList.size(); i++) {
				String inputFilePath = userCodeRootPath + File.separator + INPUT_NAME_PREFIX + i + ".txt";
				FileUtil.writeString(inputList.get(i).trim(), inputFilePath, StandardCharsets.UTF_8);
			}
		}
		/* 返回用户提交的代码文件所在的目录 */
		return userCodeFile.getParentFile().getAbsolutePath();
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
	 * java 代码运行
	 *
	 * @param codeFileParentDir 待运行 Main.class 文件存储目录
	 * @param inputList         用户输入
	 * @return List<CodeExecuteResult> 运行结果信息
	 */
	private static List<CodeExecuteResult> codeRun(String codeFileParentDir, List<String> inputList, Long timeLimit,
			Long memory) {
		List<CodeExecuteResult> messages = new ArrayList<>();
		String permissionCheckFilePath = System.getProperty("user.dir") + File.separator + "tempCodeRepository";
		if (!Files.exists(Paths.get(permissionCheckFilePath + File.separator + "DenySecurity.class"))) {
			compileDenyPermissionFile();
		}

		ExecutorService executor = Executors.newFixedThreadPool(4);
		List<Callable<CodeExecuteResult>> tasks = new ArrayList<>();
		for (int i = 0; i < inputList.size(); i++) {
			int index = i;
			tasks.add(() -> {
				String[] runCommand = ArrayUtil.append(JAVA_RUN_COMMAND, codeFileParentDir + ":" + permissionCheckFilePath,
						"-Djava.security.manager=DenyPermission", "Main");
				var processBuilder = new ProcessBuilder(runCommand);
				if (!inputList.get(index).trim().isEmpty()) {
					processBuilder
							.redirectInput(new File(codeFileParentDir + File.separator + INPUT_NAME_PREFIX + index + ".txt"));
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

					String errorMessage = ProcessUtil.getProcessOutput(runProcess.getErrorStream(), exitValue);
					// Runtime Out Of Memory
					if (errorMessage.contains("java.lang.OutOfMemoryError")) {
						exitValue = 5;
						errorMessage = errorMessage.split("release")[1];
					}

					message = messageBuilder.exitValue(exitValue)
							.testCaseId(index)
							.memory(0L)
							.time(stopWatch.getLastTaskTimeMillis())
							.normalResult(ProcessUtil.getProcessOutput(runProcess.getInputStream(), exitValue))
							.errorResult(errorMessage)
							.build();

				} else {
					stopWatch.stop(); // ----------------------------- 结束统计进程运行时间
					exitValue = 4; // Runtime Timeout
					message = messageBuilder.exitValue(exitValue)
							.testCaseId(index)
							.memory(0L)
							.time(stopWatch.getLastTaskTimeMillis())
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
