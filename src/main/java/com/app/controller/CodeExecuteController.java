package com.app.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.app.common.BaseHttpResponse;
import com.app.common.LangType;
import com.app.common.StatusEnum;
import com.app.exception.BusinessException;
import com.app.module.debug.DebugRequest;
import com.app.module.debug.DebugResponse;
import com.app.module.judge.JudgeRequest;
import com.app.module.judge.JudgeResponse;
import com.app.service.CodeSandBox;
import com.app.service.impl.DockerCodeSandBox;

import reactor.core.publisher.Mono;

/**
 * @author HDD
 * @date 2024年02月04日
 * @description 评测机接口
 */
@RestController
public class CodeExecuteController {

  public final CodeSandBox codeSandBox;

  @Autowired
  public CodeExecuteController(DockerCodeSandBox dockerCodeSandBox) {
    this.codeSandBox = dockerCodeSandBox;
  }

  /**
   * 评测机调试接口
   * @param debugRequest 调试请求
   * @return 调试结果
   */
  @PostMapping("/debug")
  public Mono<BaseHttpResponse> codeDebug(@RequestBody DebugRequest debugRequest, ServerHttpRequest sHttpRequest) {
    HttpHeaders headers = sHttpRequest.getHeaders();
    String tokioHeaderValue = headers.getFirst("token");
    if (tokioHeaderValue == null || !tokioHeaderValue.equals("AuroraOJ-HDD")) {
      throw new BusinessException(StatusEnum.NO_AUTH);
    }
    if (debugRequest.isNull()) { 
      throw new BusinessException(StatusEnum.NULL_ERROR);
    }
    LangType.getByLangName(debugRequest.getLang());
    DebugResponse debugResponse = codeSandBox.codeDebug(debugRequest);
    return BaseHttpResponse.ok(debugResponse, "调试完成");
  }
  /**
   * 评测机评审接口
   * @param judgeRequest 评测请求
   * @return 评测结果
   */
  @PostMapping("/judge")
  public Mono<BaseHttpResponse> codeJudge(@RequestBody JudgeRequest judgeRequest, ServerHttpRequest sHttpRequest) {
    HttpHeaders headers = sHttpRequest.getHeaders();
    String tokioHeaderValue = headers.getFirst("token");
    if (tokioHeaderValue == null || !tokioHeaderValue.equals("AuroraOJ-HDD")) {
      throw new BusinessException(StatusEnum.NO_AUTH);
    }
    if (judgeRequest.isNull()) { 
      throw new BusinessException(StatusEnum.NULL_ERROR);
    }
    LangType.getByLangName(judgeRequest.getLang());
    JudgeResponse judgeResponse = codeSandBox.codeJudge(judgeRequest);
    return BaseHttpResponse.ok(judgeResponse, "评审完成");
  }
}