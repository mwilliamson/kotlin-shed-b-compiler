#include <string.h>

#include "../deps/gc/include/gc.h"

#include "./shed.h"
#include "./stringbuilder.h"

void string_builder_init(struct StringBuilder* string_builder, ShedSize initial_capacity) {
    string_builder->data = GC_malloc(sizeof(uint8_t) * initial_capacity);
    string_builder->length = 0;
    string_builder->capacity = initial_capacity;
}

ShedString string_builder_build(struct StringBuilder* string_builder) {
    ShedString string = alloc_string(string_builder->length);
    string->length = string_builder->length;
    memcpy(
        string->data,
        string_builder->data,
        string_builder->length
    );
    return string;
}

void string_builder_append(struct StringBuilder* string_builder, uint8_t* data, ShedSize length) {
    ShedSize new_length = string_builder->length + length;

    if (string_builder->capacity < new_length) {
        ShedSize new_capacity = string_builder->capacity;
        while (new_capacity < new_length) {
            new_capacity *= 2;
        }

        uint8_t* new_data = GC_malloc(sizeof(uint8_t) * new_capacity);
        memcpy(
            new_data,
            string_builder->data,
            string_builder->length
        );

        string_builder->data = new_data;
        string_builder->capacity = new_capacity;
    }

    memcpy(
        &string_builder->data[string_builder->length],
        data,
        length
    );

    string_builder->length = new_length;
}
