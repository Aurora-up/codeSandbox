# 分阶段构建
# 第一阶段: 构建 rust 环境, 用来编译 execute_core 项目
FROM rust:alpine3.19 as rust_env

# rust镜像须知: https://github.com/rust-lang/docker-rust/issues/85
ENV RUSTFLAGS="-C target-feature=-crt-static"
# 为 apk 配置中科大的镜像
RUN set -eux && sed -i 's/dl-cdn.alpinelinux.org/mirrors.ustc.edu.cn/g' /etc/apk/repositories

# 安装构建依赖
RUN apk update \
    && apk add --no-cache musl-dev pkgconfig openssl-dev \
    && rm -rf /var/cache/apk/*
# 设置工作目录
WORKDIR /app
# 将本地项目移至工作目录
COPY ./execute_core /app
# 编译 execute_core 项目
RUN cargo build --release


# 第二阶段: 构建 rust 的运行时环境, 以及其他语言的运行时环境
FROM alpine:3.19
# 设置时区
ENV TZ=Asia/Shanghai
# 为 apk 配置中科大的镜像并安装 rust 运行时所需的 libgcc 库 和设置时区所需的 tzdata 库
RUN set -eux && sed -i 's/dl-cdn.alpinelinux.org/mirrors.ustc.edu.cn/g' /etc/apk/repositories \
    && apk update \
    && apk add --no-cache libgcc tzdata \
    && echo "${TZ}" > /etc/timezone \  
    && ln -sf /usr/share/zoneinfo/${TZ} /etc/localtime \
    && rm /var/cache/apk/*

# 安装 java17 运行时环境
RUN apk add --no-cache openjdk17-jre \
    && rm -rf /var/cache/apk/*

# 安装 python 运行时环境 (python 为解释性语言,直接安装解释器)
RUN apk add --no-cache python3 \
    && rm -rf /var/cache/apk/*

WORKDIR /app
# 将第一阶段构建好的可执行文件移至该运行时环境
COPY --from=rust_env /app/target/release/execute_core /execute_core/