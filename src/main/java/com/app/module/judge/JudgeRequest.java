package com.app.module.judge;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
/**
 * @author HDD
 * @date 2024年02月04日
 * @description 代码评审请求
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JudgeRequest{
	/**
	 * 测试数据
	 */
	List<TestCase> testCases;
	/**
	 * 提交代码
	 */
	String code;
	/**
	 * 编程语言
	 */
	String lang;
	/**
	 * 时间限制
	 */
	Long timeLimit;
	/**
	 * 内存限制
	 */
	Long memoryLimit;
}