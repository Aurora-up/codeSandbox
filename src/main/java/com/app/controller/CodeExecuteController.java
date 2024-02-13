package com.app.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.app.module.Result;
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
 * @description TODO
 */
@RestController
public class CodeExecuteController {

  public final CodeSandBox codeSandBox;

  @Autowired
  public CodeExecuteController(DockerCodeSandBox dockerCodeSandBox) {
    this.codeSandBox = dockerCodeSandBox;
  }

  @PostMapping("/debug")
  public Mono<Result> codeDebug(@RequestBody DebugRequest debugRequest) {
    var RSbuilder = Result.builder();
    if (debugRequest.equals(null)) { 
      return Mono.just(Result.error("请求参数为空"));
    }
    DebugResponse debugResponse = codeSandBox.codeDebug(debugRequest);
    return Mono.just(RSbuilder.statusCode(1).msg("调试完成").data(debugResponse).build());
  }
  @PostMapping("/run")
  public Mono<Result> codeDebug(@RequestBody JudgeRequest judgeRequest) {
    var RSbuilder = Result.builder();
    if (judgeRequest.equals(null)) { 
      return Mono.just(Result.error("请求参数为空"));
    }
    JudgeResponse judgeResponse = codeSandBox.codeJudge(judgeRequest);
    return Mono.just(RSbuilder.statusCode(1).msg("评审完成").data(judgeResponse).build());
  }
}
