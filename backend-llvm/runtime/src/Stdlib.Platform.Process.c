#include <string.h>

#include "../deps/gc/include/gc.h"

#include "./shed.h"

#include "../../../c-bindings/Stdlib.Lists.h"

ShedAny shed_module_fun__Stdlib__Platform__Process__args(ShedEnvironment env) {
    // TODO: deal with encoding
    ShedAny args = shed__module_value__Stdlib_Lists.nil;

    struct ShedClosure* consClosure = (struct ShedClosure*) shed__module_value__Stdlib_Lists.cons;
    ShedAny (*consFunction)(ShedEnvironment, ShedAny, ShedAny) = (ShedAny (*)(ShedEnvironment, ShedAny, ShedAny)) consClosure->function;

    for (int index = shed__argc - 1; index >= 1; index--) {
        size_t length = strlen(shed__argv[index]);
        ShedString arg = shed_string_alloc(length);
        arg->length = length;
        memcpy(arg->data, shed__argv[index], length);
        args = consFunction(&consClosure->environment[0], (ShedAny)arg, args);
    }

    return args;
}
