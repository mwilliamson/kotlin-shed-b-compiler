#include <setjmp.h>
#include <stdio.h>

int main() {
    printf("%d\n", sizeof(jmp_buf));

    return 0;
}

