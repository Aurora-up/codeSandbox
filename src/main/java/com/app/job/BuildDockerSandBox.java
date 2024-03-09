package com.app.job;

import java.io.File;
import java.nio.file.Paths;

import javax.annotation.Resource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.app.utils.DockerUtil;

/**
 * @author HDD
 * @date 2024年02月04日
 * @description 单次任务 —— 构建编译镜像和代码沙箱镜像
 */
@Component
public class BuildDockerSandBox implements ApplicationRunner{
  
	private static final String SANDBOX_DOCKER_IMAGE = "sandbox:2.0";
	private static final String SANDBOX_CONTAINER_NAME = "sandbox";
	private static final String COMPILE_ENV_DOCKER_IMAGE = "compile_env:1.0";
	private static final String COMPILE_ENV_CONTAINER_NAME = "compile_env";

  @Resource
  private DockerUtil dockerUtil;

  @Override
  public void run(ApplicationArguments args) throws Exception {
    String codeFileParentDir = new String();
    String projectDir = System.getProperty("user.dir");
    codeFileParentDir = projectDir  + File.separator + "tempCodeRepository";
    dockerUtil.getContainerId(Paths.get(codeFileParentDir), COMPILE_ENV_DOCKER_IMAGE, COMPILE_ENV_CONTAINER_NAME, 0);
    dockerUtil.getContainerId(Paths.get(codeFileParentDir), SANDBOX_DOCKER_IMAGE, SANDBOX_CONTAINER_NAME, 1);
  }
}
