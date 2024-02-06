import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class Main {
  public static void main(String[] args) {
    String directoryPath = "/home/parallels/codeSandBox/unsafe/write_exception";
    String fileName = "testWrite.txt";
    String filePath = directoryPath + "/" + fileName;
    String content = "测试写文件权限文件";
    try {
      BufferedWriter writer = new BufferedWriter(new FileWriter(filePath));
      writer.write(content);
      writer.close();
      System.out.println("文件写入成功");
    } catch (IOException e) {
      System.out.println("无法写入文件: " + e.getMessage());
    }
  }
}