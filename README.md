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