package com.app.module.execute;

import com.alibaba.fastjson2.annotation.JSONField;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author HDD
 * @date 2024年02月16日
 * @description 对应 execute_core 中的 Response
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class Response {
  /**
   * 1: 越权操作 (Permission Deny)
   * 500: rust 运行脚本异常
   * 1000: 正常执行
   * 1002: 运行时错误 (Runtime Error. RE)
   * 1003: 运行超时  (Time Limit Exceeded. TLE)
   * 1004: 运行内存超出限制 (Memory Limit Exceeded. MLE)
   */
  @JSONField(name = "exit_code")
  Integer exitCode;
  /**
   * 程序运行时间 (单位 ms)
   */
  Long time;
  /**
   * 程序运行时占用物理内存 (单位 B)
   */
  Long memory;
  /**
   * 程序输出信息 (包含正常/异常输出)
   */
  @JSONField(name = "output_msg")
  String outputMsg;
  /**
   * 测试数据 ID
   */
  @JSONField(name = "test_case_id")
  Integer testCaseId;
}
