#include "./shed.h"

ShedValue const shed_unit = 0;

struct ShedString empty_string = { .length = 0, .data = {} };

ShedString alloc_string(uint64_t capacity) {
    return GC_malloc(sizeof(StringLength) + sizeof(uint8_t) * capacity);
}
