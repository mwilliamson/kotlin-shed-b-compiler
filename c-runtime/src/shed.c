#include "./shed.h"

ShedAny const shed_unit = 0;

struct ShedString empty_string = { .length = 0, .data = {} };

ShedString shed_string_alloc(ShedSize capacity) {
    return shed_malloc(sizeof(ShedSize) + sizeof(uint8_t) * capacity, sizeof(ShedSize));
}
