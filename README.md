## CodeSandBox
### 环境准备:
1. jdk17, docker
```sh
sudo apt update

# jdk17 环境
sudo apt install openjdk-17-jdk

# mvn 安装
sudo apt install maven

# 安装 docker
sudo apt install docker.io
```
2. 将当前用户加入 Docker 组
```sh
# 创建 docker 用户组
sudo groupadd docker

# 检查 docker 用户组是否创建成功
cat /etc/group | grep 'docker'

# 将当前登录用户添加到 docker 用户组
sudo usermod -aG docker ${USER}

# 刷新用户组信息
newgrp docker

# 开启 Docker Client 的连接权限
sudo chmod 666 /var/run/docker.sock
```
### 特点
1. 多语言支持 (包括 C, C++, Java, Rust, Python3), 并且易于扩展其他语言。

2. Docker 原生代码沙箱 (针对 Dockerfile 做了专门的优化, 仅保留了必要的运行时环境, 大幅降低 Docker 镜像的大小)。

3. 基于 Rust 实现多线程异步执行脚本, 可以更加准确地获得代码的运行时间和内存占用情况, 且性能更加出色。

4. 基于 SpringWebFlux 实现接口层异步化

5. ACM 代码评测模式

### 后续可以继续优化的方面
- [ ] 抽象出代码评测的整个流程, 定义对应的抽象接口, 在去扩展其他语言或扩展其他评测模式(核心代码评测模式)时更加容易
- [ ] 去支持栈溢出判断 (目前仅有堆内存溢出判断)
- [ ] 为进一步提升评测机性能, 将用于评审的测试用例文件和正确结果文件直接存放在系统目录中, 降低测试用例在网络传输和文件IO中延迟。但是这回牺牲该评测的隔离型, 不过对于处理一些输入输出较为复杂的题目会更友好
