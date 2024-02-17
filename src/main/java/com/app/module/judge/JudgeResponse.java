package com.app.module.judge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author HDD
 * @date 2024年02月04日
 * @description 代码评审结果
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JudgeResponse {

	/**
	 * 评审结果状态 <br>
	 * 1: 越权操作 (Permission Deny) <br>
	 * 500: 判题系统出错 <br>
	 * 1000: AC  <br>
	 * 1001: 编译失败 (Compiler Error. CE)  <br>
	 * 1002: 运行失败 (Runtime Error. RE)  <br>
	 * 1003: 运行超时 (Time Limit Exceeded. TLE) <br>
	 * 1004: 运行内存超出限制 (Memory Limit Exceeded. MLE) <br>
	 * 1005: 运行成功, 答案错误 (Wrong Answer. WA) <br>
	 * 1006: 运行成功, 输出格式错误 (Presentation Error. PE)
	 */
	Integer resultStatus;

	/**
	 * 评审结果信息
	 */
	String resultMessage;

	/**
	 * 通过的测试数据数量
	 */
	Integer passTestCasesNumber;

	/**
	 * 未通过的测试数据 Id (顺序验证测试用例, 若有一个测试数据未通过,则停止运行,记录未通过的测试用例)
	 */
	Integer noPassTestCaseId;

	/**
	 * 代码运行耗时 (所有通过测试数据运行总耗时)
	 * 超时使用 -1 表示
	 */
	Long time;

	/**
	 * 代码运行占用内存 (所有通过测试数据占用内存之和)
	 * 超出内存限制使用 -1 表示
	 */
	Long memory;
}