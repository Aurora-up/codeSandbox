// use std::io::{self, BufRead, Write};

// fn main() -> io::Result<()> {
//   let stdin = io::stdin();
//   let mut reader = stdin.lock();
//   let stdout = io::stdout();
//   let mut writer = io::BufWriter::new(stdout.lock());

//   let mut input_line = String::new();
//   reader.read_line(&mut input_line)?;
//   let str1: Vec<&str> = input_line.split_whitespace().collect();
//   let a: i32 = str1[0].parse().expect("Failed to parse input");
//   let b: i32 = str1[1].parse().expect("Failed to parse input");

//   write!(writer, "{}", a + b)?;

//   Ok(())
// }
use std::fs::File;
use std::io::Write;
use std::env;

fn main() {
    // 获取当前源代码目录的路径
    let current_dir = match env::current_dir() {
        Ok(path) => path,
        Err(err) => {
            eprintln!("Error: Unable to get current directory. {}", err);
            return;
        }
    };

    // 切换到当前源代码目录
    if let Err(err) = env::set_current_dir(&current_dir) {
        eprintln!("Error: Unable to set current directory. {}", err);
        return;
    }

    let mut file = match File::create("test.txt") {
        Ok(file) => file,
        Err(err) => {
            eprintln!("Error: Unable to create file 'test.txt'. {}", err);
            return;
        }
    };

    if let Err(err) = file.write_all(b"Hello, this is a test file.\n") {
        eprintln!("Error: Unable to write to file 'test.txt'. {}", err);
        return;
    }

    println!("File 'test.txt' has been written successfully.");
}
