#include <setjmp.h>
#include <stdio.h>

int main() {
    printf("%lu\n", sizeof(jmp_buf));

    return 0;
}

