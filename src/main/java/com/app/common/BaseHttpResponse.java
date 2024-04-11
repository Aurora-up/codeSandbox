package com.app.common;

import java.io.Serializable;

import com.app.exception.BusinessException;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * @author HDD
 * @date 2024年03月7日
 * @description HTTP 响应结果基类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaseHttpResponse<T> implements Serializable {

  /**
   * 响应状态码
   */
  private Integer statusCode;

  /**
   * 状态码信息
   */
  private String message;

  /**
   * 响应数据
   */
  private T data;

  /**
   * 状态码信息 (详细)
   */
  private String description;

  /**
   * 时间戳
   */
  private Long timestamp;

  /**
   * 封装响应成功的请求
   * 
   * @param data        响应数据
   * @param description 详细描述
   * @return
   */
  public static <T> Mono<BaseHttpResponse<T>> ok(T data, String description) {
    var builder = new BaseHttpResponseBuilder<T>();
    var resp = builder.statusCode(StatusEnum.SUCCESS.getStatusCode())
        .data(data)
        .message(StatusEnum.SUCCESS.getMessage())
        .description(description)
        .timestamp(System.currentTimeMillis())
        .build();
    return Mono.just(resp);
  }

	/**
	 * 封装响应失败的请求 -- 已定义异常
	 * @param e 异常
	 * @return 响应结果
	 */
  public static Mono<BaseHttpResponse<BusinessException>> error(BusinessException e) {
    var builder = new BaseHttpResponseBuilder<BusinessException>();
    var resp = builder.statusCode(e.getStatusCode())
        .message(StatusEnum.getMessageByStatusCode(e.getStatusCode()))
        .description(e.getDescription())
        .timestamp(System.currentTimeMillis())
        .build();
    return Mono.just(resp);
  }

	/**
	 * 封装响应失败的请求 -- 系统内部未知异常
	 * @param e 异常
	 * @return 响应结果
	 */
  public static Mono<BaseHttpResponse<RuntimeException>> error(RuntimeException e) {
    var builder = new BaseHttpResponseBuilder<RuntimeException>();
    var resp = builder.statusCode(StatusEnum.SYSTEM_ERROR.getStatusCode())
        .message(StatusEnum.SYSTEM_ERROR.getMessage())
        .description(e.getMessage())
        .timestamp(System.currentTimeMillis())
        .build();
    return Mono.just(resp);
  }
}
