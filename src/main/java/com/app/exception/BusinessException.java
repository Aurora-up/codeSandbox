package com.app.exception;

import com.app.common.StatusEnum;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * @author HDD
 * @date 2024年03月7日
 * @description 自定义业务异常类
 */
@Getter
@Builder
@AllArgsConstructor
public class BusinessException extends RuntimeException {
  /**
   * 状态码
   */
  private final Integer statusCode;

  /**
   * 异常描述信息
   */
  private final String description;

  public BusinessException(String message, int code, String description) {
    super(message);
    this.statusCode = code;
    this.description = description;
  }

  public BusinessException(StatusEnum status) {
    super(status.getMessage());
    this.statusCode = status.getStatusCode();
    this.description = status.getDescription();
  }

  public BusinessException(StatusEnum status, String description) {
    super(status.getMessage());
    this.statusCode = status.getStatusCode();
    this.description = description;
  }
}
