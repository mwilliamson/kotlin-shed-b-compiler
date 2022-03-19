#include <string.h>

#include "shed.h"
#include "strings.h"
#include "./stringbuilder.h"

void string_builder_init(struct StringBuilder* string_builder, ShedSize initial_capacity) {
    string_builder->data = shed_malloc(sizeof(uint8_t) * initial_capacity, 1);
    string_builder->length = 0;
    string_builder->capacity = initial_capacity;
}

ShedString string_builder_build(struct StringBuilder* string_builder) {
    ShedString string = shed_string_alloc(string_builder->length);
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

        uint8_t* new_data = shed_malloc(sizeof(uint8_t) * new_capacity, 1);
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
