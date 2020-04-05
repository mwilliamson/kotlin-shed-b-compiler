#include <string.h>

#include "../deps/gc/include/gc.h"

#include "./shed.h"
#include "./stringbuilder.h"

struct StringBuilderStack {
    struct StringBuilder string_builder;
    struct StringBuilderStack* next;
};

static struct StringBuilderStack* string_builder_stack = NULL;

ShedString Shed_Stdlib_Platform_StringBuilder_build(ShedEnvironment env, struct ShedClosure* closure) {
    StringLength initial_capacity = 16;

    struct StringBuilderStack* new_stack = GC_malloc(sizeof(struct StringBuilderStack));
    string_builder_init(&new_stack->string_builder, initial_capacity);
    new_stack->next = string_builder_stack;
    string_builder_stack = new_stack;

    ShedValue (*func)(ShedEnvironment) = (ShedValue (*)(ShedEnvironment)) closure->function;
    func(closure->environment);

    string_builder_stack = string_builder_stack->next;

    return string_builder_build(&new_stack->string_builder);
}

ShedValue Shed_Stdlib_Platform_StringBuilder_write(ShedEnvironment env, ShedString value) {
    string_builder_append(&string_builder_stack->string_builder, value->data, value->length);
    return shed_unit;
}
