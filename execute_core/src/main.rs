#![allow(unused)]
use base64::engine::general_purpose;
use base64::Engine;
use execute_core::entity::{RequestArgs, Response};
use psutil::process::Process;
use std::future::IntoFuture;
use std::process::{exit, Stdio};
use std::sync::Arc;
use std::time::Duration;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::process::Command;
use tokio::sync::{mpsc, Mutex};
use tokio::task;
use tokio::time::timeout;

/// @description: 监控线程 <br>
/// @params: <br>
///     pid: 执行进程 ID <br>
///     memory_limit: 内存限制 <br>
///     sender: 监控结果发送管道 <br>
async fn monitor_thread(pid: u32, memory_limit: u64, sender: mpsc::Sender<(u128, u64)>) {
    let mut use_time: u128 = 1; // 执行时间小于 1ms 的统一为 1ms
    let mut use_memory: u64 = 0;
    if let Ok(process_util) = Process::new(pid) {
        while process_util.is_running() {
            if let Ok(_time) = process_util.cpu_times() {
                use_time = std::cmp::max(use_time, _time.user().as_millis());
            }
            if let Ok(_memory) = process_util.memory_info() {
                use_memory = std::cmp::max(use_memory, _memory.rss());
            }
            /* 内存超出限制 */
            if use_memory > memory_limit {
                use_memory = memory_limit + 1;
                let _ = sender.send((use_time, use_memory)).await;
                process_util.kill().unwrap();
                break;
            }
            tokio::time::sleep(Duration::from_micros(500)).await;
        }
    }
    // 发送时间和内存信息
    let _ = sender.send((use_time, use_memory)).await;
}

/// @description: 测试数据执行进程 <br>
/// @params: <br>
///     file_dir: 执行进程需要文件所在的目录 <br>
///     test_case_id: 当前测试数据 ID <br>
///     lang: 编程语言 <br>
///     memory_limit: 内存限制 <br>
///     time_limit: 时间限制 <br>
///     result_sender: 发送管道 <br>
async fn child_process(
    file_dir: String,
    test_case_id: u32,
    lang: u32,
    memory_limit: u64,
    time_limit: u64,
    result_sender: mpsc::Sender<Response>,
) {
    let mut response = Response::new();
    response.set_test_case_id(test_case_id);

    /* 读取测试数据  */
    let mut input_data = String::new();
    let input_filename = file_dir.clone() + "input-" + &test_case_id.to_string() + ".txt";
    let mut input_file = match tokio::fs::File::open(input_filename).await {
        Ok(file) => file,
        Err(e) => {
            eprintln!("无法打开测试数据文件: {}", e);
            exit(1);
        }
    };
    if let Err(e) = input_file.read_to_string(&mut input_data).await {
        eprintln!("无法读取测试数据文件: {}", e);
        exit(1);
    }

    /* 匹配编程语言对应执行命令 */
    let mut run_command: Command;
    let mut command_args: Option<&[&str]> = None;
    let java_args = ["-cp", file_dir.as_str(), "Main"];

    match lang {
        1 => {
            run_command = Command::new(file_dir.clone() + "main");
        }
        2 => {
            run_command = Command::new("java");
            command_args = Some(&java_args);
        }
        _ => {
            run_command = Command::new("python3");
        }
    }

    /* 开始执行进程 */
    let mut run_process = match command_args {
        Some(args) => run_command.args(args),
        None => &mut run_command,
    }
    .stdin(Stdio::piped())
    .stdout(Stdio::piped())
    .stderr(Stdio::piped())
    .spawn()
    .expect("执行命令异常");

    if let Some(mut stdin) = run_process.stdin.take() {
        stdin
            .write_all(input_data.as_bytes())
            .await
            .expect("无法将测试用例重定向至执行进程的输入");
    }

    let pid = run_process.id().expect("无法获取到执行进程 ID");

    /* 定义监控结果的收发管道 */
    let (sender, mut receiver) = mpsc::channel::<(u128, u64)>(2);
    let sender = Arc::new(Mutex::new(sender));

    let cloned_sender = Arc::clone(&sender);

    // 开启监控线程
    tokio::spawn(monitor_thread(
        pid,
        memory_limit,
        cloned_sender.lock().await.clone(),
    ));

    // 设置时间限制
    match timeout(
        Duration::from_millis(time_limit.try_into().unwrap()),
        run_process.wait(),
    )
    .await
    {
        Ok(process_result) => match process_result {
            /* 获取进程执行状态 */
            Ok(process_status) => {
                /* 正常输出 */
                if process_status.success() {
                    let mut normal_output = String::new();
                    if let Some(mut stdout) = run_process.stdout.take() {
                        response.set_exit_code(1000);
                        stdout.read_to_string(&mut normal_output).await.unwrap();
                        response.set_output_msg(general_purpose::STANDARD.encode(normal_output));
                    }
                }
                /* 异常输出 */
                else {
                    let mut error_output = String::new();
                    if let Some(mut stderr) = run_process.stderr.take() {
                        response.set_exit_code(1002);
                        stderr.read_to_string(&mut error_output).await.unwrap();
                        response.set_output_msg(general_purpose::STANDARD.encode(error_output));
                    }
                }
                /* 内存溢出输出 */
                if let Some((use_time, use_memory)) = receiver.recv().await {
                    response.set_time(use_time);
                    response.set_memory(use_memory);
                    if use_memory == memory_limit + 1 {
                        response.set_exit_code(1004);
                        response.set_output_msg("Memory Exceeded Limit");
                    }
                }

                result_sender.send(response).await.unwrap();
            }
            Err(err) => {
                eprintln!("无法获取进程状态: {}", err);
            }
        },
        Err(_) => {
            if run_process.try_wait().unwrap().is_none() {
                run_process
                    .kill()
                    .await
                    .expect("超出时间限制后终止线程异常");
            }

            if let Some((_, use_memory)) = receiver.recv().await {
                response.set_memory(use_memory);
            }

            response.set_exit_code(1003);
            response.set_time((time_limit + 1).into());
            response.set_output_msg("Time Exceeded Limit");

            result_sender.send(response).await.unwrap();
        }
    }
}

#[tokio::main]
async fn main() {
    /* 获取 request_args.json 所在目录 */
    let json_args_dir = task::block_in_place(|| {
        let mut input = String::new();
        std::io::stdin()
            .read_line(&mut input)
            .expect("无法读取到 request_args.json 的所在目录");
        input.trim().to_string()
    });
    /* 获取执行请求参数 */
    let mut request_args_file = tokio::fs::File::open(json_args_dir + "request_args.json")
        .await
        .expect("无法打开 request_args.json 文件");
    let mut request_args_string = String::new();
    request_args_file
        .read_to_string(&mut request_args_string)
        .await
        .expect("无法读取 request_args.json 文件");
    let request_args: RequestArgs = serde_json::from_str(&request_args_string).unwrap();

    /* 定义各个测试用例结果的收发管道, 初始化双倍数量的缓冲区, 避免缓冲区阻塞导致部分输出丢失 */
    let (result_sender, mut result_receiver) =
        mpsc::channel::<Response>(request_args.test_case_num as usize + 10);

    let file_dir = request_args.file_dir;
    /* 多线程异步执行 */
    for test_case_id in 1..=request_args.test_case_num {
        let cloned_sender = result_sender.clone();
        tokio::spawn(child_process(
            file_dir.clone(),
            test_case_id as u32,
            request_args.lang as u32,
            request_args.memory_limit,
            request_args.time_limit as u64,
            cloned_sender,
        ));
    }
    drop(result_sender); // 销毁管道, 防止阻塞

    /* 收集结果 */
    let mut responses: Vec<Response> = vec![];
    while let Some(result) = result_receiver.recv().await {
        responses.push(result);
    }
    if let Ok(output_json) = serde_json::to_string(&responses) {
        println!("{}", general_purpose::STANDARD.encode(output_json));
    } else {
        eprintln!("Failed to convert responses to JSON");
    }
}