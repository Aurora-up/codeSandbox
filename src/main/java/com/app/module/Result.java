package com.app.module;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Result {
  private Integer statusCode;
  private String msg;
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
