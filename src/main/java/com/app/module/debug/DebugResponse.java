package com.app.module.debug;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author HDD
 * @date 2024年02月04日
 * @description 代码调试结果
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class DebugResponse {
	/**
	 * 调试结果状态 <br>
	 * 0: 编译, 运行都正常 <br>
	 * 1: 权限不足 / 含有危险代码 (Permission Deny / Include Malicious Code) <br>
	 * 2: 编译失败 (Compile Error) <br>
	 * 3: 编译成功, 运行失败 (Runtime Error) <br>
	 * 4: 编译成功, 运行超时 (Run Timeout)  <br>
	 * 5: 编译成功, 运行内存超出限制 (Out Of Memory) <br>
	 */
	Integer resultStatus;
	/**
	 * 调试结果信息
	 */
	String resultMessage;

	/**
	 * 代码运行耗时 (单位: ms)
	 */
	Long time;

	/**
	 * 代码运行占用内存 (单位: B)
	 */
	Long memory;
}