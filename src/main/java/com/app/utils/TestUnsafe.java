package com.app.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

public class TestUnsafe {
  public static void main(String[] args) throws IOException {
    var in = new BufferedReader(new InputStreamReader(System.in));
    var out = new PrintWriter(new OutputStreamWriter(System.out));
    String[] str1 = in.readLine().split(" ");
    int a = Integer.parseInt(str1[0]);
    int b = Integer.parseInt(str1[1]);
    out.print(a + b);
    out.flush();
    out.close();
    in.close();

  }
}
