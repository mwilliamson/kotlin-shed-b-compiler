#include <string.h>

#include "shed.h"

ShedString shed_string_alloc(ShedSize capacity) {
    return shed_malloc(sizeof(ShedSize) + sizeof(uint8_t) * capacity, sizeof(ShedSize));
}

ShedString shed_string_add(ShedString left, ShedString right) {
    ShedSize length = left->length + right->length;
    ShedString result = shed_string_alloc(length);
    result->length = length;

    memcpy(result->data, left->data, left->length);
    memcpy(&result->data[left->length], right->data, right->length);

    return result;
}

ShedBool shed_string_equals(ShedString left, ShedString right) {
    if (left->length != right->length) {
        return 0;
    } else {
        for (ShedSize index = 0; index < left->length; index++) {
            if (left->data[index] != right->data[index]) {
                return 0;
            }
        }
        return 1;
    }
}

ShedBool shed_string_not_equal(ShedString left, ShedString right) {
    return !shed_string_equals(left, right);
}
