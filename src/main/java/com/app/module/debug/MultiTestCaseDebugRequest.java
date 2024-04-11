package com.app.module.debug;

import java.util.List;

import com.app.module.judge.TestCase;
import java.util.function.Function;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author HDD
 * @date 2024年04月09日
 * @description 多测试用例调试请求
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class MultiTestCaseDebugRequest {
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
	 * 时间限制 (单位 : ms)
	 */
	Long timeLimit;
	/**
	 * 内存限制 (单位: B)
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
		if ((this.testCases == null || this.testCases.isEmpty() || testCastIsNull.apply(this.testCases)) ||
				(this.code == null || this.code.trim().equals("")) ||
				(this.timeLimit == null) ||
				(this.memoryLimit == null) ||
				(this.lang == null || this.lang.trim().equals(""))) {
			return true;
		}
		return false;
	}
}
