pub mod entity {
    #![allow(unused)]
    use serde::{Deserialize, Serialize};

    /**
     * 执行请求参数
     */
    #[derive(Debug, Serialize, Deserialize, Clone)]
    pub struct RequestArgs {
        /**
         * 语言类型:
         *  1: 编译后直接是可执行文件: c , c++ , rust
         *  2: 需要在 jvm 运行编译后的文件: java
         *  3: 解释型语言 python
         */
        pub lang: u8,
        /**
         * 测试数据数量
         */
        pub test_case_num: u8,
        /**
         * 时间限制 (单位: ms)
         */
        pub time_limit: u128,
        /**
         * 内存限制 (单位: B)
         */
        pub memory_limit: u64,
        /**
         * 隔离存放的目录
         */
        pub file_dir: String,
    }

    /**
     * 执行结果
     */
    #[derive(Debug, Serialize, Deserialize, Clone)]
    pub struct Response {
        /**
         * 500: rust 运行脚本异常
         * 1000: 正常执行
         * 1002: 运行时错误 (Runtime Error. RE) 包括越权操作造成的运行时异常
         * 1003: 运行超时  (Time Limit Exceeded. TLE)
         * 1004: 运行内存超出限制 (Memory Limit Exceeded. MLE)
         */
        pub exit_code: u16,
        /**
         * 程序运行时间 (单位 ms)
         */
        pub time: u128,
        /**
         * 程序运行时占用物理内存 (单位 B)
         */
        pub memory: u64,
        /**
         * 程序输出信息 (包含正常/异常输出)
         */
        pub output_msg: String,
        /**
         * 测试数据 ID
         */
        pub test_case_id: u32,
    }
    impl Response {
        pub fn from(
            exit_code: u16,
            time: u128,
            memory: u64,
            output_msg: String,
            test_case_id: u32,
        ) -> Self {
            Response {
                exit_code,
                time,
                memory,
                output_msg,
                test_case_id,
            }
        }
        pub fn new() -> Self {
            Response {
                exit_code: 500, // 默认 rust 执行脚本出错
                time: 0,
                memory: 0,
                output_msg: String::new(),
                test_case_id: 0,
            }
        }

        pub fn set_exit_code(&mut self, exit_code: u16) -> &mut Self {
            self.exit_code = exit_code;
            self
        }

        pub fn set_time(&mut self, time: u128) -> &mut Self {
            self.time = time;
            self
        }

        pub fn set_memory(&mut self, memory: u64) -> &mut Self {
            self.memory = memory;
            self
        }

        pub fn set_output_msg<S>(&mut self, output_msg: S) -> &mut Self
        where
            S: Into<String>,
        {
            self.output_msg = output_msg.into();
            self
        }
        pub fn set_test_case_id(&mut self, id: u32) -> &mut Self {
            self.test_case_id = id;
            self
        }
    }
}
