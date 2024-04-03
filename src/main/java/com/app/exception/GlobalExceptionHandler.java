package com.app.exception;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.app.common.BaseHttpResponse;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * @author HDD
 * @date 2024年03月07日
 * @description 全局异常处理类
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
  /**
   * 处理已定义的异常状态
   * @param e
   * @return
   */
  @ExceptionHandler(BusinessException.class)
  public Mono<BaseHttpResponse<BusinessException>> businessExceptionHandler(BusinessException e) {
    log.error("接口请求出现异常: " + e.getMessage());
    return BaseHttpResponse.error(e);
  }

  /**
   * 处理未定义的系统运行时异常
   * @param e
   * @return
   */
  @ExceptionHandler(RuntimeException.class)
  public Mono<BaseHttpResponse<RuntimeException>> runtimeExceptionHandler(RuntimeException e) {
    log.error("系统内部出现未知异常" + e.getMessage());
    return BaseHttpResponse.error(e);
  }
}
