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
public class BaseHttpResponse implements Serializable {

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
  private Object data;

  /**
   * 状态码信息 (详细)
   */
  private String description;

  /**
   * 封装响应成功的请求
   * @param data 响应数据
   * @param description 详细描述
   * @return
   */
  public static Mono<BaseHttpResponse> ok(Object data, String description) {
    var builder = new BaseHttpResponseBuilder();
    var resp = builder.statusCode(StatusEnum.SUCCESS.getStatusCode())
                  .data(data)
                  .message(StatusEnum.SUCCESS.getMessage())
                  .description(description)
                  .build();
    return Mono.just(resp);
  }

  /**
   * 封装响应失败的请求
   * @param errorStatu 错误状态
   * @param description 错误描述
   * @return
   */
  public static Mono<BaseHttpResponse> error(BusinessException e) {
    var builder = new BaseHttpResponseBuilder();
    var resp = builder.statusCode(e.getStatusCode())
                  .message(e.getMessage())
                  .description(e.getDescription())
                  .build();
    return Mono.just(resp);
  }
}
