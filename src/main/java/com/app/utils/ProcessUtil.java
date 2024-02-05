package com.app.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * @author HDD
 * @date 2024年02月04日
 * @description 进程工具类
 */
public class ProcessUtil {
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
			e.printStackTrace();
		}
		return out.toString();
	}
	public static long getMemoryUsage(Process runProcess) {
		try {
			long pid = runProcess.toHandle().pid();
			System.out.println("++++++++++++++" + runProcess.toHandle().pid());
			long maxMemoryUsage = 0;

			// 循环获取特定进程的内存使用情况
			while (runProcess.isAlive()) {
				// 获取特定进程的内存使用情况
				long memoryUsage = getMemoryUsage(pid);
				System.out.println("Process Memory Used: " + memoryUsage + "B");

				// 更新最大内存使用值
				maxMemoryUsage = Math.max(maxMemoryUsage, memoryUsage);

				// 等待一段时间
				Thread.sleep(200);
			}

			return maxMemoryUsage;
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			return -1; // 返回负值表示出错
		}
	}


	private static long getMemoryUsage(long pid) throws IOException {
		String command = "ps -p " + pid + " -o rss=";
		Process process = new ProcessBuilder("bash", "-c", command).start();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String memoryUsage = reader.readLine();
			return Long.parseLong(memoryUsage);
		}
	}
}