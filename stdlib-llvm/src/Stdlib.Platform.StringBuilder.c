#include <string.h>

#include "../deps/gc/include/gc.h"

#include "./shed.h"

// TODO: implement stack
static struct ShedString* string_builder_string = NULL;

ShedString Shed_Stdlib_Platform_StringBuilder_build(ShedEnvironment env, struct ShedClosure* closure) {
    // TODO: dynamically grow data
    string_builder_string = alloc_string(1024 * 1024);
    string_builder_string->length = 0;

    ShedValue (*func)(ShedEnvironment) = (ShedValue (*)(ShedEnvironment)) closure->function;

    func(closure->environment);

    return string_builder_string;
}

ShedValue Shed_Stdlib_Platform_StringBuilder_write(ShedEnvironment env, ShedString value) {
    memcpy(
        &string_builder_string->data[string_builder_string->length],
        value->data,
        value->length
    );

    string_builder_string->length += value->length;

    return shed_unit;
}
