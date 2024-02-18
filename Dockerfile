# 使用 rust:slim 作为基础镜像
FROM rust:slim

WORKDIR /app

RUN apt-get update && \
    apt-get install -y openjdk-17-jdk-headless && \
    rm -rf /var/lib/apt/lists/*

RUN apt-get update && \
    apt-get install -y python3 && \
    ln -s /usr/bin/python3 /usr/bin/python && \
    rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH=$PATH:$JAVA_HOME/bin