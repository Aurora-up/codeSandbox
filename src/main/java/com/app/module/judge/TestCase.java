package com.app.module.judge;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author HDD
 * @date 2024年02月04日
 * @description 测试数据, T 为结果类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestCase {
	/**
	 * 测试数据 ID
	 */
	Integer id;
	/**
	 * 测试数据内容
	 */
	String input;
	/**
	 * 测试数据的正确结果
	 */
	String correctResult;

	/**
	 * 内容判空
	 * @return
	 */
	public Boolean isNull() {
		if ((this.id == null) && 
				(this.input == null || this.input.trim().equals("")) && 
				(this.correctResult == null || this.correctResult.trim().equals(""))) {
			return true;
		}
		return false;
	}
}