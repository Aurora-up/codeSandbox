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
		TestCase testCase1 = new TestCase(1,"2 5", "7");
		TestCase testCase2 = new TestCase(2,"4 5", "9");
		String testFilePath = "/home/parallels/codeSandBox/src/main/resources/testCode/main.cpp";
		String code = FileUtil.readString(testFilePath ,StandardCharsets.UTF_8);
		req.setLang("cpp");
		req.setCode(code);
		req.setTestCases(List.of(testCase1, testCase2));
		req.setTimeLimit(1000L);
		req.setMemoryLimit(64 * 1024 * 1024L);
		var judgeResponse = rustCodeSandBox.codeJudge(req);
		System.out.println(judgeResponse);
	}
}
