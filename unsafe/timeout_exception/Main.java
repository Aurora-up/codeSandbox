public class Main {
  public static void main(String[] args) {
    System.out.println("程序开始");
    try {
      // 睡 10 秒
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    System.out.println("程序结束");
  }
}