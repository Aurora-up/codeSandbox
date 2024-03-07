package com.app.module.execute;

import com.alibaba.fastjson2.annotation.JSONField;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author HDD
 * @date 2024年02月16日
 * @description 对应 execute_core 中的 RequestArgs
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestArgs {
  /**
   * 语言类型:
   *  1: 编译后直接是可执行文件: c , c++ , rust
   *  2: 需要在 jvm 运行编译后的文件: java
   *  3: 解释型语言 python
   */
  @JSONField(name = "lang")
  Integer lang;
  /**
   * 测试数据数量
   */
  @JSONField(name = "test_case_num")
  Integer testCaseNum;
  /**
   * 时间限制 (单位: ms)
   */
  @JSONField(name = "time_limit")
  Long timeLimit;
  /**
   * 内存限制 (单位: B)
   */
  @JSONField(name = "memory_limit")
  Long memoryLimit;
  /**
   * 隔离存放的目录
   */
  @JSONField(name = "file_dir")
  String fileDir;
}
