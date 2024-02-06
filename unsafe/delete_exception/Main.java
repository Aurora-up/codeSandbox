import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;

/**
 * 测试禁止删除权限是否成功
 */
public class Main {
  public static void main(String[] args) {
    String filePath = "/home/parallels/codeSandBox/unsafe/delete_exception/testDelete.txt";
    Path path = Paths.get(filePath);
    try {
      Files.delete(path);
      System.out.println("文件删除成功");
    } catch (IOException e) {
      System.out.println("无法删除文件: " + e.getMessage());
    }
  }
}