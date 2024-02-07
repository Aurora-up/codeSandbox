import java.util.Scanner;

public class Main {
	public static void main(String[] args) {
		Scanner scanner = new Scanner(System.in);

		// 读取物品数量和背包容量
		int N = scanner.nextInt();
		int V = scanner.nextInt();

		// 物品的体积和价值数组
		int[] volumes = new int[N];
		int[] values = new int[N];

		// 读取物品的体积和价值
		for (int i = 0; i < N; i++) {
			volumes[i] = scanner.nextInt();
			values[i] = scanner.nextInt();
		}

		// 创建一个二维数组dp，dp[i][j]表示前i种物品，体积不超过j的情况下的最大价值
		int[][] dp = new int[N + 1][V + 1];

		// 动态规划过程
		for (int i = 1; i <= N; i++) {
			for (int j = 0; j <= V; j++) {
				// 不选第i种物品
				dp[i][j] = dp[i - 1][j];

				// 选择第i种物品
				if (j >= volumes[i - 1]) {
					dp[i][j] = Math.max(dp[i][j], dp[i][j - volumes[i - 1]] + values[i - 1]);
				}
			}
		}
//		Thread sleepThread = new Thread(() -> {
//			try {
//				Thread.sleep(3000); // Sleep for 5 seconds
//				System.out.println("Wake up!");
//			} catch (InterruptedException e) {
//				e.printStackTrace();
//			}
//		});
//
//		sleepThread.start(); // Start the new thread
//
//		// Do other work in the main thread if needed
//
//		try {
//			sleepThread.join(); // Wait for the sleepThread to finish
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}

		// 输出结果
		System.out.println(dp[N][V]);
	}
}