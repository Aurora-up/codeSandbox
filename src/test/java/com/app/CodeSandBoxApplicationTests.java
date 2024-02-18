package com.app;

import cn.hutool.core.io.FileUtil;
import com.app.module.debug.DebugRequest;
import com.app.module.debug.DebugResponse;
import com.app.module.judge.JudgeRequest;
import com.app.module.judge.JudgeResponse;
import com.app.module.judge.TestCase;
import com.app.service.impl.DockerCodeSandBox;
import com.app.service.impl.LocalCodeSandBox;
import com.app.service.impl.RustCodeSandBox;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;


import java.nio.charset.StandardCharsets;
import java.util.List;


@SpringBootTest
public class CodeSandBoxApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void localCodeSandBoxDebugTest() {
		var localCodeSandBox = new LocalCodeSandBox();
		var req = new DebugRequest();
		req.setInput("3 4\n");  // 正确结果: 8;   4 5\n1 2\n2 4\n3 4\n4 5
		String testFilePath = "/home/parallels/codeSandBox/src/main/resources/testCode/Main.java";
		String code = FileUtil.readString(testFilePath ,StandardCharsets.UTF_8);
		req.setCode(code);
		DebugResponse debugResponse = localCodeSandBox.codeDebug(req);
		System.out.println(debugResponse);
	}

	@Test
	void localCodeSandBoxRunTest() {
		var localCodeSandBox = new LocalCodeSandBox();
		var judgeRequest = new JudgeRequest();
		TestCase testCase0 = new TestCase(0,"2 5\n1 2\n2 4", "6");
		TestCase testCase1 = new TestCase(1,"4 5\n1 2\n2 4\n3 4\n4 5", "8");
		String testFilePath = "/home/parallels/codeSandBox/src/main/resources/testCode/Main.java";
		String code = FileUtil.readString(testFilePath ,StandardCharsets.UTF_8);
		judgeRequest.setTestCases(List.of(testCase0, testCase1));
		judgeRequest.setCode(code);
		judgeRequest.setTimeLimit(1000L);
		judgeRequest.setMemoryLimit(64 * 1024 * 1024L);
		JudgeResponse judgeResponse = localCodeSandBox.codeJudge(judgeRequest);
		System.out.println(judgeResponse);
	}

	@Test
	void dockerCodeSandBoxDebugTest() {
		var dockerCodeSandBox = new DockerCodeSandBox();
		var req = new DebugRequest();
		req.setInput("4 5\n1 2\n2 4\n3 4\n4 5");  // 正确结果: 8   错误输入测试: "4 5\n1 2\n2 4"
		String testFilePath = "/home/parallels/codeSandBox/src/main/resources/testCode/Main.java";
		String code = FileUtil.readString(testFilePath ,StandardCharsets.UTF_8);
		req.setCode(code);
		DebugResponse debugResponse = dockerCodeSandBox.codeDebug(req);
		System.out.println(debugResponse);
	}
	@Test
	void dockerCodeSandBoxRunTest() {
		var dockerCodeSandBox = new DockerCodeSandBox();
		var judgeRequest = new JudgeRequest();
		TestCase testCase0 = new TestCase(0,"2 5\n1 2\n2 4", "6");
		TestCase testCase1 = new TestCase(1,"4 5\n1 2\n2 4\n3 4\n4 5", "8");
		String testFilePath = "/home/parallels/codeSandBox/src/main/resources/testCode/Main.java";
		String code = FileUtil.readString(testFilePath ,StandardCharsets.UTF_8);
		judgeRequest.setTestCases(List.of(testCase0, testCase1));
		judgeRequest.setCode(code);
		judgeRequest.setTimeLimit(1000L);
		judgeRequest.setMemoryLimit(64 * 1024 * 1024L);
		JudgeResponse judgeResponse = dockerCodeSandBox.codeJudge(judgeRequest);
		System.out.println(judgeResponse);
	}
	@Test
	void rustCodeSandBoxDebugTest() {
		var rustCodeSandBox = new RustCodeSandBox();
		var req = new DebugRequest();
		req.setInput("4 5");  // 正确结果: 8   错误输入测试: "4 5\n1 2\n2 4"
		String testFilePath = "/home/parallels/codeSandBox/src/main/resources/testCode/main.rs";
		String code = FileUtil.readString(testFilePath ,StandardCharsets.UTF_8);
		req.setLang("rust");
		req.setCode(code);
		DebugResponse debugResponse = rustCodeSandBox.codeDebug(req);
		System.out.println(debugResponse);
	}
	@Test
	void rustCodeSandBoxRunTest() {
		var rustCodeSandBox = new RustCodeSandBox();
		var req = new JudgeRequest();
		TestCase testCase1 = new TestCase(1,"4 5\n1 2\n 2 4\n3 4\n 4 5\n", "10");
		TestCase testCase2 = new TestCase(2,"4 5\n1 2\n 2 4\n3 4\n 4 5\n", "10");
		TestCase testCase3 = new TestCase(3,"4 5\n1 2\n 2 4\n3 4\n 4 5\n", "10");
		TestCase testCase4 = new TestCase(4,"4 5\n1 2\n 2 4\n3 4\n 4 5\n", "10");
		TestCase testCase5 = new TestCase(5,"4 5\n1 2\n 2 4\n3 4\n 4 5\n", "10");
		TestCase testCase6 = new TestCase(6,"4 5\n1 2\n 2 4\n3 4\n 4 5\n", "10");
		TestCase testCase7 = new TestCase(7,"4 5\n1 2\n 2 4\n3 4\n 4 5\n", "10");
		TestCase testCase8 = new TestCase(8,"4 5\n1 2\n 2 4\n3 4\n 4 5\n", "10");
		TestCase testCase9 = new TestCase(9,"4 5\n1 2\n 2 4\n3 4\n 4 5\n", "10");
		TestCase testCase10 = new TestCase(10,"4 5\n1 2\n 2 4\n3 4\n 4 5\n", "10");
		TestCase testCase11 = new TestCase(11,"4 5\n1 2\n 2 4\n3 4\n 4 5\n", "10");
		TestCase testCase12 = new TestCase(12,"4 5\n1 2\n 2 4\n3 4\n 4 5\n", "10");
		TestCase testCase13 = new TestCase(13,"4 5\n1 2\n 2 4\n3 4\n 4 5\n", "10");
		TestCase testCase14 = new TestCase(14,"4 5\n1 2\n 2 4\n3 4\n 4 5\n", "10");
		TestCase testCase15 = new TestCase(15,"4 5\n1 2\n 2 4\n3 4\n 4 5\n", "10");
		TestCase testCase16 = new TestCase(16,"4 5\n1 2\n 2 4\n3 4\n 4 5\n", "10");
		String testFilePath = "/home/parallels/codeSandBox/src/main/resources/testCode/main.cpp";
		String code = FileUtil.readString(testFilePath ,StandardCharsets.UTF_8);
		req.setLang("cpp");
		req.setCode(code);
		req.setTestCases(List.of(testCase1, testCase2, testCase3, testCase4, testCase5, 
		testCase6, testCase7, testCase8, testCase9, testCase10, testCase11, testCase12, testCase13,
		testCase14, testCase15, testCase16));
		req.setTimeLimit(1000L);
		req.setMemoryLimit(64 * 1024 * 1024L);
		var judgeResponse = rustCodeSandBox.codeJudge(req);
		System.out.println(judgeResponse);
	}
}
