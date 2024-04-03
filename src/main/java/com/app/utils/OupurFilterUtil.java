package com.app.utils;

public class OupurFilterUtil {
  /**
   * 处理编译错误的输出 (删除编译输出中带有的文件路径, 防止文件路径泄漏)
   * 
   * @param errorResult 原编译错误信息
   * @param langType    语言
   * @return 删除文件路径的编译错误的输出
   */
  public static String tackleCompileOutput(String errorResult, String langType) {
    String perfix = new String("main");
    String subfix = new String();
    switch (langType) {
      case "java":
        perfix = "Main";
        subfix = ".java";
        break;
      case "cpp":
        subfix = ".cpp";
        break;
      case "c":
        subfix = ".c";
        break;
      case "rust":
        subfix = ".rs";
        break;
      default:
        break;
    }
    String[] compileError = errorResult.split(perfix + subfix);
    if (compileError.length >= 2) {
      return perfix + subfix + compileError[1];
    }
    return errorResult;
  }

  /**
   * 处理使用 Java Security Manager 时警告输出
   * 由于使用 Java Security Manager 会带来 java 代码运行时间统计上的误差，故而弃用
   * 
   * @param errorResult 错误输出
   * @return 删除警告后的输出
   */
  @Deprecated
  public static String tackleJavaRuntimeErrorOutput(String errorResult) {
    String[] runtimeException = errorResult.split("release");
    if (runtimeException.length >= 2) {
      return runtimeException[1];
    }
    return errorResult;
  }

  /**
   * 处理使用 Java Security Manager 捕获到了权限异常
   * 由于使用 Java Security Manager 会带来 java 代码运行时间统计上的误差，故而弃用
   * 
   * @param errorResult
   * @return
   */
  @Deprecated
  public static String tackleJavaPermissionDenyInfo(String errorResult) {
    String[] permissionException = errorResult.split("#as#");
    if (permissionException.length >= 2) {
      return permissionException[1];
    }
    return errorResult;
  }
}
