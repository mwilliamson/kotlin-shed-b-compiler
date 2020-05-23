#include <setjmp.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "../deps/gc/include/gc.h"
#include "../deps/utf8proc/utf8proc.h"

#include "./shed.h"

static ShedValue returnValue = 0;

static jmp_buf buffer;

ShedValue Shed_Stdlib_Platform_NonLocalReturn_run(ShedEnvironment env, struct ShedClosure* func, struct ShedClosure* onNonLocalReturn) {
    int status = setjmp(buffer);
    if (status == 0) {
        ShedValue (*funcFunction)(ShedEnvironment) = (ShedValue (*)(ShedEnvironment)) func->function;
        return funcFunction(func->environment);
    } else {
        ShedValue (*onNonLocalReturnFunction)(ShedEnvironment, ShedValue) = (ShedValue (*)(ShedEnvironment, ShedValue)) onNonLocalReturn->function;
        return onNonLocalReturnFunction(onNonLocalReturn->environment, returnValue);
    }
}

void Shed_Stdlib_Platform_NonLocalReturn_nonLocalReturn(ShedEnvironment env, ShedValue value) {
    returnValue = value;
    longjmp(buffer, 1);
}
