#ifndef SHED_H
#define SHED_H

#include "shed_platform.h"

struct ShedString {
    ShedSize length;
    uint8_t data[];
};

typedef struct ShedString* ShedString;

struct ShedStringSlice {
    ShedString string;
    ShedSize startIndex;
    ShedSize endIndex;
};

typedef struct ShedStringSlice* ShedStringSlice;

extern void* shed_malloc(ShedSize, uint32_t alignment);

ShedString shed_string_alloc(ShedSize capacity);

#endif
