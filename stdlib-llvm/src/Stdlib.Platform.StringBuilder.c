#include <string.h>

#include "../deps/gc/include/gc.h"

#include "./shed.h"

struct StringBuilderStack {
    ShedString string;
    struct StringBuilderStack* next;
};

static struct StringBuilderStack* string_builder_string_stack = NULL;

ShedString Shed_Stdlib_Platform_StringBuilder_build(ShedEnvironment env, struct ShedClosure* closure) {
    // TODO: dynamically grow data

    ShedString string_builder_string = alloc_string(1024 * 1024);
    string_builder_string->length = 0;

    struct StringBuilderStack* new_stack = GC_malloc(sizeof(struct StringBuilderStack));
    new_stack->string = string_builder_string;
    new_stack->next = string_builder_string_stack;
    string_builder_string_stack = new_stack;

    ShedValue (*func)(ShedEnvironment) = (ShedValue (*)(ShedEnvironment)) closure->function;
    func(closure->environment);

    string_builder_string_stack = string_builder_string_stack->next;

    return string_builder_string;
}

ShedValue Shed_Stdlib_Platform_StringBuilder_write(ShedEnvironment env, ShedString value) {
    ShedString string = string_builder_string_stack->string;
    memcpy(
        &string->data[string->length],
        value->data,
        value->length
    );

    string->length += value->length;

    return shed_unit;
}
