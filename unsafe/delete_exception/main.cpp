#include <iostream>
#include <filesystem>

int main() {
  // 指定文件路径和文件名
  std::filesystem::path filePath = "/home/parallels/codeSandBox/unsafe/delete_exception/testDelete.txt";

  try {
    // 使用remove函数删除文件
    std::filesystem::remove(filePath);
    std::cout << "文件删除成功" << std::endl;
  } catch (const std::filesystem::filesystem_error& e) {
      std::cerr << "文件删除失败: " << e.what() << std::endl;
  }

  return 0;
}
