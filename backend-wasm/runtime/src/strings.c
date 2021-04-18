#include "./shed.h"

void* memcpy(void* dest, const void* src, unsigned long n) {
    for (ShedSize i = 0; i < n; i++) {
        ((uint8_t*)dest)[i] = ((const uint8_t*)src)[i];
    }

    return dest;
}

ShedString shed_string_alloc(uint32_t capacity) {
    return shed_malloc(sizeof(ShedSize) + sizeof(uint8_t) * capacity, 4);
}

ShedString shed_string_add(ShedString left, ShedString right) {
    ShedSize length = left->length + right->length;
    ShedString result = shed_string_alloc(length);
    result->length = length;

    memcpy(result->data, left->data, left->length);
    memcpy(&result->data[left->length], right->data, right->length);

    return result;
}
