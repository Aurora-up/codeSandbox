package com.app;

import java.util.regex.Pattern;

import cn.hutool.core.util.ArrayUtil;
import lombok.Data;

public class TestJson {
  public static void main(String[] args) {
    String s1 = " 1  \t  2 \n ";
    String s2 = "\t 1  \t\t  2";
    String regex = "\\s+";
    Pattern pattern = Pattern.compile(regex);
    System.out.println(ArrayUtil.equals(pattern.split(s1), pattern.split(s2)));
  }
}


@Data
class ExecuteResult {
    private int exitCode;
    private int time;
    private long memory;
    private String outputMsg;
    private int testCaseId;
}
