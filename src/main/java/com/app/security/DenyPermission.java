package com.app.security;

@SuppressWarnings({ "removal" })
public class DenyPermission extends SecurityManager {

  /**
   * 禁止去执行 Shell 脚本
   */
  public void checkExec(String filePath) {
    String[] fileName = filePath.split("/");
    throw new SecurityException(
        "#as#Permission Deny;" + " Execute Command " + fileName[fileName.length - 1] + " is disable!!!#as#");
  }

  /**
   * 仅允许 java 进程读取 Main.class 文件
   */
  public void checkRead(String file) {
    String[] fileName = file.split("/");
    if (fileName[fileName.length - 1].contains("Main"))
      return;
    else {
      throw new SecurityException("#as#Permission Deny;" + " Read " + fileName[fileName.length - 1] + " is disable!!!#as#");
    }
  }

  /**
   * 禁止 Main.java 写文件
   */
  public void checkWrite(String filePath) {
    String[] fileName = filePath.split("/");
    throw new SecurityException("#as#Permission Deny;" + " Write " + fileName[fileName.length - 1] + " is disable!!!#as#");
  }

  /**
   * 禁止 Main.java 删除其他文件
   */
  @Override
  public void checkDelete(String filePath) {
    String[] fileName = filePath.split("/");
    throw new SecurityException("#as#Permission Deny;" + " Delete " + fileName[fileName.length - 1] + " is disable!!!#as#");
  }
}