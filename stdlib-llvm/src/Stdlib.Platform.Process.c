#include <string.h>

#include "../deps/gc/include/gc.h"
#include "../deps/utf8proc/utf8proc.h"

#include "./shed.h"

#include "../../c-bindings/Stdlib.Lists.h"

ShedValue Shed_Stdlib_Platform_Process_args() {
    // TODO: deal with encoding
    ShedValue args = shed__module_value__Stdlib_Lists.nil;

    struct ShedClosure* consClosure = (struct ShedClosure*) shed__module_value__Stdlib_Lists.cons;
    ShedValue (*consFunction)(ShedEnvironment, ShedValue, ShedValue) = (ShedValue (*)(ShedEnvironment, ShedValue, ShedValue)) consClosure->function;

    for (int index = shed__argc - 1; index >= 1; index--) {
        size_t length = strlen(shed__argv[index]);
        ShedString arg = alloc_string(length);
        arg->length = length;
        memcpy(arg->data, shed__argv[index], length);
        args = consFunction(&consClosure->environment[0], (ShedValue)arg, args);
    }

    return args;
}
