package com.app.module;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author HDD
 * @date 2024年02月14日
 * @description HTTP 响应结果
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Result {
  /**
   * 响应状态: 0: 失败; 1: 成功
   */
  private Integer statusCode;
  /**
   * 响应附带信息
   */
  private String msg;
  /**
   * 响应数据
   */
  private Object data;
  public static Result success() {
    return new Result(1, "success", null);
  }
  public static Result success(Object data) {
    return new Result(1, "success", data);
  }
  public static Result error(String msg) {
    return new Result(0, msg, null);
  }
}
