package com.app.common;

import java.util.Arrays;

import com.app.exception.BusinessException;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author HDD
 * @date 2024年03月7日
 * @description HTTP 响应状态
 */
@AllArgsConstructor
@Getter
public enum StatusEnum {
  SUCCESS(0, "ok", ""),
  PARAMS_ERROR(40000, "请求参数错误", ""),
  NULL_ERROR(40001, "请求数据为空", ""),
  NO_AUTH(40003, "你没有权限", ""),
  SYSTEM_ERROR(50000, "系统内部异常", ""),
  SYSTEM_NOT_IMPLEMENTED(50001, "系统暂不支持该语言", "");

  /**
   * 响应状态码
   */
  private final int statusCode;

  /**
   * 状态码信息
   */
  private final String message;

  /**
   * 状态码信息（详情）
   */
  private final String description;

  /**
   * 根据状态吗获取状态码信息
   * 
   * @param statusCode 状态码
   */
  public static String getMessageByStatusCode(int statusCode) {
    return Arrays.stream(StatusEnum.values())
                .filter(e -> {
                  if (e.getStatusCode() == statusCode) {
                    return true;
                  }
                  return false;
                })
                .findFirst()
                .orElseThrow(() -> {
                  throw new BusinessException(StatusEnum.SYSTEM_ERROR);
                })
                .getMessage();
  }

}
