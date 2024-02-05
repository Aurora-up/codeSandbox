package com.app.service;

import com.app.module.debug.DebugRequest;
import com.app.module.debug.DebugResponse;
import com.app.module.judge.JudgeRequest;
import com.app.module.judge.JudgeResponse;
import org.springframework.stereotype.Component;

/**
 * @author HDD
 * @date 2024年02月04日
 * @description 代码沙箱接口
 */
@Component
public interface CodeSandBox {
	/**
	 * 代码调试
	 * @param debugRequest 代码调试请求
	 * @return 代码调试结果
	 */
	DebugResponse codeDebug(DebugRequest debugRequest);

	/**
	 * 代码评审
	 * @param judgeRequest 代码评审请求
	 * @return 代码评审结果
	 */
	JudgeResponse codeJudge(JudgeRequest judgeRequest);
}