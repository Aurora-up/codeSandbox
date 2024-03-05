FROM rust:alpine3.19 as rust_env

ENV RUSTFLAGS="-C target-feature=-crt-static"
# 为 apk 配置中科大的镜像
RUN set -eux && sed -i 's/dl-cdn.alpinelinux.org/mirrors.ustc.edu.cn/g' /etc/apk/repositories

# 安装 c, cpp, rust 的编译环境
RUN apk update \
    && apk add --no-cache musl-dev pkgconfig openssl-dev build-base\
    && rm -rf /var/cache/apk/*
# 设置工作目录
WORKDIR /app