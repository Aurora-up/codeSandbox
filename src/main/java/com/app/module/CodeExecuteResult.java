package com.app.module;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author HDD
 * @date 2024年02月04日
 * @description TODO
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class CodeExecuteResult {
	/**
	 * 测试数据 Id
	 */
	Integer testCaseId;
	/**
	 * 进程执行结果退出码 <br>
	 * exitValue == 0 时: 进程运行正常, 否则进程运行异常
	 */
	Integer exitValue;
	/**
	 * 进程执行正常输出结果
	 */
	String normalResult;
	/**
	 * 进程执行异常输出结果
	 */
	String errorResult;
	/**
	 * 进程执行耗时 (单位 : ms)
	 */
	Long time;
	/**
	 * 进程执行占用内存 (单位: B)
	 */
	Long memory;
}