import java.util.ArrayList;
import java.util.List;

public class Main {
  public static void main(String[] args) {
    List<byte[]> memoryList = new ArrayList<>();
    // 无限分配
    try {
      while (true) {
        byte[] memoryChunk = new byte[1024 * 1024];
        memoryList.add(memoryChunk);
      }
    } catch (OutOfMemoryError | InterruptedException e) {
      e.printStackTrace();
    }
  }
}
