package com.app.module.debug;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author HDD
 * @date 2024年02月04日
 * @description 代码调试请求
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DebugRequest {
	/**
	 * 测试用例
	 */
	String input;
	/**
	 * 待调试代码
	 */
	String code;
	/**
	 * 代码的编程语言
	 */
	String lang;
}