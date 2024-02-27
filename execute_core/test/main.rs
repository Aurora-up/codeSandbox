use std::io::{self, BufRead, Write};

fn main() -> io::Result<()> {
  let stdin = io::stdin();
  let mut reader = stdin.lock();
  let stdout = io::stdout();
  let mut writer = io::BufWriter::new(stdout.lock());

  let mut input_line = String::new();
  reader.read_line(&mut input_line)?;
  let str1: Vec<&str> = input_line.split_whitespace().collect();
  let a: i32 = str1[0].parse().expect("Failed to parse input");
  let b: i32 = str1[1].parse().expect("Failed to parse input");

  write!(writer, "{}", a + b)?;

  Ok(())
}