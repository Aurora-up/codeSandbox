package com.app;

import java.io.IOException;

public class TestOshi {
	public static void main(String[] args) {
		// Create a new process (replace with your own process creation code)
		ProcessBuilder processBuilder = new ProcessBuilder("java", "--version");
		try {
			System.out.println(processBuilder.start().toHandle().info().totalCpuDuration().get());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
