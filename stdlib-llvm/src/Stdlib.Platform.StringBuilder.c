#include <string.h>

#include "../deps/gc/include/gc.h"

#include "./shed.h"

struct StringBuilderStack {
    uint8_t* data;
    StringLength length;
    StringLength capacity;
    struct StringBuilderStack* next;
};

static struct StringBuilderStack* string_builder_string_stack = NULL;

ShedString Shed_Stdlib_Platform_StringBuilder_build(ShedEnvironment env, struct ShedClosure* closure) {
    StringLength initial_capacity = 16;

    struct StringBuilderStack* new_stack = GC_malloc(sizeof(struct StringBuilderStack));
    new_stack->data = GC_malloc(sizeof(uint8_t) * initial_capacity);
    new_stack->length = 0;
    new_stack->capacity = initial_capacity;
    new_stack->next = string_builder_string_stack;
    string_builder_string_stack = new_stack;

    ShedValue (*func)(ShedEnvironment) = (ShedValue (*)(ShedEnvironment)) closure->function;
    func(closure->environment);

    string_builder_string_stack = string_builder_string_stack->next;

    ShedString string = alloc_string(new_stack->length);
    string->length = new_stack->length;
    memcpy(
        string->data,
        new_stack->data,
        new_stack->length
    );
    return string;
}

ShedValue Shed_Stdlib_Platform_StringBuilder_write(ShedEnvironment env, ShedString value) {
    StringLength new_length = string_builder_string_stack->length + value->length;

    if (string_builder_string_stack->capacity < new_length) {
        StringLength new_capacity = string_builder_string_stack->capacity;
        while (new_capacity < new_length) {
            new_capacity *= 2;
        }

        uint8_t* new_data = GC_malloc(sizeof(uint8_t) * new_capacity);
        memcpy(
            new_data,
            string_builder_string_stack->data,
            string_builder_string_stack->length
        );

        string_builder_string_stack->data = new_data;
        string_builder_string_stack->capacity = new_capacity;
    }

    memcpy(
        &string_builder_string_stack->data[string_builder_string_stack->length],
        value->data,
        value->length
    );

    string_builder_string_stack->length = new_length;

    return shed_unit;
}
