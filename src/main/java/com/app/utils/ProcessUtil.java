package com.app.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.app.common.StatusEnum;
import com.app.exception.BusinessException;

/**
 * @author HDD
 * @date 2024年02月04日
 * @description 进程工具类
 */
public class ProcessUtil {
	/**
	 * 内存监控线程类
	 */
	@Deprecated
	public static class MemoryMonitor implements Runnable {
		private Process process;
		private long peakMemoryUsage;

		public MemoryMonitor(Process process) {
			this.process = process;
			this.peakMemoryUsage = 0;
		}

		@Override
		public void run() {
			try {
				/* 进程一直存活时, 获取执行进程实际使用的物理内存大小 */
				while (process.isAlive()) {
					ProcessBuilder psBuilder = new ProcessBuilder("ps", "-p", String.valueOf(process.pid()), "-o", "rss=");
					Process daemon = psBuilder.start();

					try (InputStream inputStream = daemon.getInputStream()) {
						String output = getProcessOutput(inputStream, 0);
						if (output.trim() != "") {
							long memoryUsage = Long.parseLong(output.trim()) * 1024;
							peakMemoryUsage = Math.max(peakMemoryUsage, memoryUsage);
						}
					}

					daemon.waitFor();
					Thread.sleep(20); // 每 20ms 监控一次
				}
			} catch (IOException |InterruptedException e) {
				e.printStackTrace();
			}
		}

		public long getPeakMemoryUsage() {
			return peakMemoryUsage;
		}
	}
	/**
	 * 获取 (编译/运行) 进程的 (正常/异常) 输出
	 * @param in (正常/异常) 输出流
	 * @param exitValue 进程退出代码
	 * @return (正常/异常) 输出
	 */
	public static String getProcessOutput(InputStream in, int exitValue) {
		var reader = new BufferedReader(new InputStreamReader(in));
		var out = new StringBuilder();
		String line;
		try {
			while ((line = reader.readLine()) != null) {
				if (exitValue == 0) {
					out.append(line);
				}else {
					out.append(line).append("\n");
				}
			}
		} catch (IOException e) {
			throw new BusinessException(StatusEnum.SYSTEM_ERROR, "获取进程输出异常. " + e);
		}
		return out.toString();
	}
}