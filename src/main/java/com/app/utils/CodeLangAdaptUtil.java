package com.app.utils;

import java.io.File;

import com.app.common.LangType;
import com.app.common.StatusEnum;
import com.app.exception.BusinessException;

import cn.hutool.core.util.ArrayUtil;

/**
 * @author HDD
 * @date 2024年02月27日
 * @description 语言类型适配工具类
 */
public class CodeLangAdaptUtil {
  /* rust */
  private static final String RUST_CODE_FILE_NAME = "main.rs";
	private static final String[] RUST_COMPILE_COMMAND = new String[] {"rustc"};

  /* cpp */
	private static final String CPP_CODE_FILE_NAME = "main.cpp";
	private static final String[] CPP_COMPILE_COMMAND = new String[] {"g++"};
	
  /* c */
  private static final String C_CODE_FILE_NAME = "main.c";
	private static final String[] C_COMPILE_COMMAND = new String[] {"gcc"};
	
  /* java */
  private static final String JAVA_CODE_FILE_NAME = "Main.java";
	private static final String[] JAVA_COMPILE_COMMAND = new String[] {"javac", "-encoding", "utf-8"};
	
  /* python */
  private static final String PYTHON_CODE_FILE_NAME = "main.py";
  
  /**
   * 不同编程语言存储文件名适配
   * @param langType 语言类型枚举
   * @return 存储文件名
   */
  public static String codeStoreFileNameAdapt(LangType langType){
    String codeStoreFileName = new String();
    switch (langType) {
      case RUST:
        codeStoreFileName = RUST_CODE_FILE_NAME;
        break;
      case CPP:
        codeStoreFileName = CPP_CODE_FILE_NAME;   
        break; 
      case C:
        codeStoreFileName = C_CODE_FILE_NAME;
        break;
      case JAVA:
        codeStoreFileName = JAVA_CODE_FILE_NAME;
        break;
      case PYTHON:
        codeStoreFileName = PYTHON_CODE_FILE_NAME;
        break;
      default:
        throw new BusinessException(StatusEnum.SYSTEM_NOT_IMPLEMENTED);
    }
    return codeStoreFileName;
  }

  /**
   * 不同编程语言的 "编译命令" 适配
   * @param langType 语言类型枚举
   * @param compileFilePath 待编译文件存储路径
   * @return 编译命令
   */
  public static String[] codeCompileCommandArgsAdapt(LangType langType, String compileFilePath) {
    String[] compileCommand = new String[]{};
    switch (langType) {
      case RUST:
        compileCommand = ArrayUtil.append(
          RUST_COMPILE_COMMAND, 
          "-o", 
          compileFilePath + File.separator + "main",
          compileFilePath + File.separator + RUST_CODE_FILE_NAME);
        break;

      case CPP:
        compileCommand = ArrayUtil.append(
          CPP_COMPILE_COMMAND, 
          compileFilePath + File.separator + CPP_CODE_FILE_NAME,
          "-o", 
          compileFilePath + File.separator + "main");
        break;

      case C:
        compileCommand = ArrayUtil.append(
          C_COMPILE_COMMAND, 
          compileFilePath + File.separator + C_CODE_FILE_NAME,
          "-o", 
          compileFilePath + File.separator + "main");
        break;
      
      case JAVA:
        compileCommand = ArrayUtil.append(
          JAVA_COMPILE_COMMAND, 
          compileFilePath + File.separator + JAVA_CODE_FILE_NAME);
        break;
      default:
        throw new BusinessException(StatusEnum.SYSTEM_NOT_IMPLEMENTED);
    }
    return compileCommand;
  }
}
