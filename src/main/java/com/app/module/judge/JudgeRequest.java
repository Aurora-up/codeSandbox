package com.app.module.judge;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.function.Function;
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
	/**
	 * 内容判空
	 * @return
	 */
	public Boolean isNull() {
		Function<List<TestCase>, Boolean> testCastIsNull = (List<TestCase> testCases) -> {
			for (var e: testCases) {
				if (e.isNull()) {
					return true;
				}
			}
			return false;
		};
		if ((this.testCases.isEmpty() || testCastIsNull.apply(this.testCases)) &&
				(this.code == null || this.code.trim().equals("")) && 
				(this.timeLimit == null) && 
				(this.memoryLimit == null) && 
				(this.lang == null || this.lang.trim().equals(""))) {
			return true;
		}
		return false;
	}
}