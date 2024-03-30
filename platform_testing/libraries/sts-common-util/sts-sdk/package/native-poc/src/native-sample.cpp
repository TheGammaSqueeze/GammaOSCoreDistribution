#include <iostream>
#include <fstream>

#define EXIT_SUCCESS 0
#define EXIT_FAILURE 1
#define EXIT_VULNERABLE 113

int main(int argc, char *argv[]) {
    if (argc != 3) {
        return EXIT_FAILURE;
    }
    std::ifstream f(argv[1]);
    if (f.is_open()) {
        std::cout << "Hello " << f.rdbuf() << "! " << argv[2] << std::endl;
        return EXIT_SUCCESS;
    } else {
        return EXIT_VULNERABLE;
    }
}
