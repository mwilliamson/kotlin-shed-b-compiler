#include <stdbool.h>
#include <string.h>

//#include "../deps/gc/include/gc.h"
//#include "../deps/utf8proc/utf8proc.h"

#include "./shed.h"
#include "./strings.h"

#include "../../../c-bindings/Stdlib.Lists.h"

typedef uint32_t __wasi_size_t;
typedef uint16_t __wasi_errno_t;

int32_t __imported_wasi_snapshot_preview1_args_get(int32_t arg0, int32_t arg1) __attribute__((
    __import_module__("wasi_snapshot_preview1"),
    __import_name__("args_get")
));

__wasi_errno_t __wasi_args_get(
    uint8_t * * argv,
    uint8_t * argv_buf
){
    int32_t ret = __imported_wasi_snapshot_preview1_args_get((int32_t) argv, (int32_t) argv_buf);
    return (uint16_t) ret;
}

int32_t __imported_wasi_snapshot_preview1_args_sizes_get(int32_t arg0, int32_t arg1) __attribute__((
    __import_module__("wasi_snapshot_preview1"),
    __import_name__("args_sizes_get")
));

__wasi_errno_t __wasi_args_sizes_get(
    __wasi_size_t *retptr0,
    __wasi_size_t *retptr1
){
    int32_t ret = __imported_wasi_snapshot_preview1_args_sizes_get((int32_t) retptr0, (int32_t) retptr1);
    return (uint16_t) ret;
}

ShedAny shed_module_fun__Stdlib__Platform__Process__args(ShedEnvironment env) {
    // TODO: deal with encoding
    ShedAny args = shed__module_value__Stdlib_Lists.nil;

    struct ShedClosure* consClosure = (struct ShedClosure*) shed__module_value__Stdlib_Lists.cons;
    ShedAny (*consFunction)(ShedEnvironment, ShedAny, ShedAny) = (ShedAny (*)(ShedEnvironment, ShedAny, ShedAny)) consClosure->function;

    __wasi_size_t argCount;
    __wasi_size_t argsStringLength;
    __wasi_args_sizes_get(&argCount, &argsStringLength);
    // TODO: error checking
    uint8_t** argStringPointers = shed_malloc(sizeof(uint8_t*) * argCount, sizeof(uint8_t*));
    uint8_t* argsString = shed_malloc(argsStringLength, sizeof(uint8_t));
    __wasi_args_get(argStringPointers, argsString);
    // TODO: error checking

    for (__wasi_size_t argIndex = argCount - 1; argIndex >= 1; argIndex--) {
        uint8_t* startPointer = argStringPointers[argIndex];
        bool isLast = argIndex + 1 == argCount;
        uint8_t* endPointer = isLast
            ? argsStringLength + argsString
            : argStringPointers[argIndex + 1];
        // strings from args_get() are null terminated, hence the - 1
        __wasi_size_t length = endPointer - startPointer - 1;
        ShedString arg = shed_string_alloc(length);
        arg->length = length;
        memcpy(arg->data, startPointer, length);
        args = consFunction(&consClosure->environment[0], (ShedAny)arg, args);
    }

    return args;
}
