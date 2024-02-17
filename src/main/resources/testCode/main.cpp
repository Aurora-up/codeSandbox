// #include<bits/stdc++.h>
// using namespace std;

// int main(){
//   int a, b;
//   cin >> a >> b;
//   cout << a + b;
//   return 0;
// }
#include <iostream>
#include <fstream>

int main() {
    std::ofstream file("test.txt");

    if (file.is_open()) {
        file << "Hello, this is a test file." << std::endl;
        file.close();
        std::cout << "File 'test.txt' has been written successfully." << std::endl;
    } else {
        std::cerr << "Unable to open file 'test.txt' for writing." << std::endl;
    }

    return 0;
}
