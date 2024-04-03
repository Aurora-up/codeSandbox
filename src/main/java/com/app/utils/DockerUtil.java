package com.app.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.app.common.StatusEnum;
import com.app.exception.BusinessException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.BuildImageResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@SuppressWarnings("deprecation")
public class DockerUtil {
	public static final String CODE_STORE_ROOT_PATH = "tempCodeRepository";
	public static DockerClient dockerClient;
	static {
		/* 创建代码存放的 "根目录" 的绝对路径 */
		String projectDirPath = System.getProperty("user.dir");
		String codeStoreRootPath = projectDirPath + File.separator + CODE_STORE_ROOT_PATH;
		if (!FileUtil.exist(codeStoreRootPath)) {
			FileUtil.mkdir(codeStoreRootPath);
		}
		/* 初始化 Docker 客户端 */
		try {
			DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
			DockerHttpClient dockerHttpClient = new ApacheDockerHttpClient.Builder()
					.dockerHost(config.getDockerHost())
					.sslConfig(config.getSSLConfig())
					.maxConnections(3000) // 最大连接数根据需求设定
					.build();
			dockerClient = DockerClientBuilder.getInstance(config).withDockerHttpClient(dockerHttpClient).build();
		} catch (Exception e) {
			throw new BusinessException(StatusEnum.SYSTEM_ERROR, "初始化 Docker Client 错误" + e);
		}
		/* java 代码运行时权限限制 */
		// String permissionCheckFilePath = System.getProperty("user.dir") +
		// File.separator + "tempCodeRepository"
		// + File.separator + "DenyPermission.class";
		// if (!Files.exists(Paths.get(permissionCheckFilePath))) {
		// compileDenyPermissionFile();
		// }
	}

	/**
	 * 根据 Dockerfile 文件构建 编译/运行时 镜像
	 * 
	 * @param imageName 镜像名
	 * @param flag      0: 编译镜像, 1: 运行时镜像
	 */
	private static void createImage(String imageName, Integer flag) {
		String projectDirPath = System.getProperty("user.dir");

		File DockerfilePath = null;
		if (flag.equals(0))
			DockerfilePath = new File(projectDirPath + File.separator + "compile_env" + File.separator + "Dockerfile");
		else
			DockerfilePath = new File(projectDirPath + File.separator + "Dockerfile");

		System.out.println("Dockerfile 文件路径： " + DockerfilePath);

		BuildImageCmd buildImageCmd = dockerClient.buildImageCmd(DockerfilePath);
		Set<String> name = new HashSet<String>();
		name.add(imageName);
		buildImageCmd.withTags(name);
		var buildImageResultCallback = new BuildImageResultCallback() {
			@Override
			public void onNext(BuildResponseItem item) {
				if (flag.equals(0))
					log.info("创建编译环境镜像中: " + item.toString());
				else
					log.info("创建代码沙箱环境镜像中: " + item.toString());
				super.onNext(item);
			}
		};
		try {
			buildImageCmd.exec(buildImageResultCallback)
					.awaitCompletion();
			if (flag.equals(0))
				log.info("编译环境创建成功");
			else
				log.info("代码沙箱创建成功");
		} catch (InterruptedException e) {
			if (flag.equals(0))
				throw new BusinessException(StatusEnum.SYSTEM_ERROR, "创建编译环境镜像失败" + e);
			else
				throw new BusinessException(StatusEnum.SYSTEM_ERROR, "创建代码沙箱环境镜像失败" + e);
		}
	}

	/**
	 * 根据镜像创建容器
	 * 
	 * @param codeFileParentDir 容器挂载目录
	 * @param imageName         镜像名
	 * @param containerName     容器名
	 * @param flag              0: 编译镜像, 1: 运行时镜像
	 * @return 创建的容器的 ID
	 */
	private static String createContainer(Path codeFileParentDir, String imageName, String containerName,
			Integer flag) {
		var containerCmd = dockerClient.createContainerCmd(imageName).withName(containerName);
		var hostConfig = new HostConfig();
		log.info("挂载目录:" + codeFileParentDir.toString());

		hostConfig.setBinds(
				new Bind(codeFileParentDir.toString(), new Volume("/codeStore")));
		hostConfig.withMemory(256 * 1024 * 1024L);
		hostConfig.withCpuCount(1L);

		// 编译环境
		if (flag.equals(0)) {
			CreateContainerResponse containerInstance = null;
			try {
				containerInstance = containerCmd
						.withReadonlyRootfs(true)
						.withHostConfig(hostConfig)
						.withNetworkDisabled(true)
						.withAttachStdin(true)
						.withAttachStdout(true)
						.withAttachStderr(true)
						.withTty(true)
						.exec();
			} catch (Exception e) {
				throw new BusinessException(StatusEnum.SYSTEM_ERROR, "编译容器创建失败" + e);
			}
			return containerInstance.getId();
		}
		// 运行环境
		else {
			String seccompProfile = null;
			try {
				String projectDir = System.getProperty("user.dir");
				String seccompProfilePath = projectDir + File.separator + "src" + File.separator + "main"
						+ File.separator + "resources" + File.separator + "permission" + File.separator
						+ "seccomp_profile_for_container.json";
				seccompProfile = new String(Files.readAllBytes(Paths.get(seccompProfilePath)));
			} catch (IOException e) {
				throw new BusinessException(StatusEnum.SYSTEM_ERROR, "无法读取到 seccomp 安全配置文件" + e);
			}
			hostConfig.withSecurityOpts(List.of("seccomp=" + seccompProfile));
			CreateContainerResponse containerInstance = null;
			try {
				containerInstance = containerCmd
						.withReadonlyRootfs(true)
						.withHostConfig(hostConfig)
						.withNetworkDisabled(true)
						.withAttachStdin(true)
						.withAttachStdout(true)
						.withAttachStderr(true)
						.withTty(true)
						.exec();
			} catch (Exception e) {
				throw new BusinessException(StatusEnum.SYSTEM_ERROR, "沙箱容器创建失败" + e);
			}
			return containerInstance.getId();
		}
	}

	/**
	 * 获取容器ID
	 * 
	 * @param codeFileParentDir 容器挂载目录
	 * @param imageName         镜像名
	 * @param containerName     容器名
	 * @param flag              0: 编译镜像, 1: 运行时镜像
	 * @return 容器ID
	 */
	public String getContainerId(Path codeFileParentDir, String imageName, String containerName, Integer flag) {
		var listContainersCmd = dockerClient.listContainersCmd().withNameFilter(List.of(containerName))
				.withShowAll(true);
		var listImageCmd = dockerClient.listImagesCmd().withReferenceFilter(imageName);

		List<Image> existedImage = listImageCmd.exec();
		List<Container> existedContainer = listContainersCmd.exec();

		String containerId = new String();
		if (existedImage.isEmpty() && existedContainer.isEmpty()) {
			createImage(containerId, flag);
			containerId = createContainer(codeFileParentDir, imageName, containerName, flag);
		} else if (!existedImage.isEmpty() && existedContainer.isEmpty()) {
			containerId = createContainer(codeFileParentDir, imageName, containerName, flag);
		} else {
			containerId = existedContainer.get(0).getId();
		}
		// 并启动容器
		var containerInfo = dockerClient.inspectContainerCmd(containerId).exec();
		if (Boolean.FALSE.equals(containerInfo.getState().getRunning())) {
			try {
				dockerClient.startContainerCmd(containerId).exec();
			} catch (Exception e) {
				if (flag.equals(0)) {
					throw new BusinessException(StatusEnum.SYSTEM_ERROR, "编译容器启动失败" + e);
				} else {
					throw new BusinessException(StatusEnum.SYSTEM_ERROR, "沙箱容器启动失败" + e);
				}
			}
		}
		return containerId;
	}
}
