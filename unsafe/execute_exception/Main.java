import java.io.IOException;

public class Main {
  public static void main(String[] args) {
    String directoryPath = "/home/parallels/codeSandBox/unsafe/execute_exception/testExecute.sh";
    String scriptName = "testExecute.sh";
    String scriptPath = directoryPath + "/" + scriptName;
    try {
      ProcessBuilder processBuilder = new ProcessBuilder(scriptPath);
      processBuilder.directory(new java.io.File(directoryPath));
      Process process = processBuilder.start();
      int exitCode = process.waitFor();
      if (exitCode == 0) {
        System.out.println("脚本执行成功");
      } else {
        System.out.println("脚本执行失败，退出码: " + exitCode);
      }
    } catch (IOException | InterruptedException e) {
      System.out.println("无法执行脚本: " + e.getMessage());
    }
  }
}
