## CodeSandBox
环境准备:
1. jdk17, g++, gcc, rust, docker

```sh
sudo apt update

# gcc, g++ 环境
sudo apt install build-essential

# jdk17 环境
sudo apt install openjdk-17-jdk

# mvn 安装
sudo apt install maven

# rust 环境
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

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
sudo gpasswd -a ${USER}

# 刷新用户组信息
newgrp docker
```
3. 运行 Dockerfile 文件, 创建隔离的沙箱环境
```sh
cd /home/<user_name>/codeSandBox

docker built -t sandbox .
```

```sh
# 编译 cargo 项目
cd /home/<user_name>/codeSandBox/execute_core
cargo build --release
cp -r /home/<user_name>/codeSandBox/execute_core/target/release /home/<user_name>/codeSandBox/tempCodeRepository/execute_core
```