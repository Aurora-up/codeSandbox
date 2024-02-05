package com.app;

import lombok.extern.slf4j.Slf4j;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;
import oshi.util.FormatUtil;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class TestOshi {
	public static void main(String[] args) {
		ExecutorService service =  Executors.newSingleThreadScheduledExecutor();

		service.execute(() -> {
			ProcessBuilder javaProcess = new ProcessBuilder("java", "--version");
			try {
				Process process1 = javaProcess.start();
				while(process1.isAlive()) {
					SystemInfo systemInfo = new SystemInfo();
					OperatingSystem os = systemInfo.getOperatingSystem();
					int processId = (int) process1.pid();
					OSProcess process = os.getProcess(processId);

					log.info("pid %d 当前进程：占用内存 %s ".formatted(processId, FormatUtil.formatBytes(process.getResidentSetSize())));

					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						log.error("性能监控时出现异常：{}",e);
					}
				}
				process1.waitFor();
			} catch (IOException | InterruptedException e) {
				throw new RuntimeException(e);
			}

		});
	}
}
