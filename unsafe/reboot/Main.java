import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        try {
            // 使用 Runtime 类执行系统命令
            Process process = Runtime.getRuntime().exec("sudo shutdown -r now");

            // 可以使用 process 对象获取命令执行的输出等信息
            // process.getInputStream(), process.getErrorStream(), process.waitFor() 等

            // 等待命令执行完毕
            int exitCode = process.waitFor();

            // 输出命令执行结果
            System.out.println("Command exit code: " + exitCode);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
