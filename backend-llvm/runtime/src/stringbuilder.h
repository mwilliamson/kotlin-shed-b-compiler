#ifndef STRINGBUILDER_H
#define STRINGBUILDER_H

#include <stdint.h>

#include "./shed.h"

struct StringBuilder {
    uint8_t* data;
    StringLength length;
    StringLength capacity;
};

void string_builder_init(struct StringBuilder* string_builder, StringLength initial_capacity);
ShedString string_builder_build(struct StringBuilder* string_builder);
void string_builder_append(struct StringBuilder* string_builder, uint8_t* data, StringLength length);

#endif
