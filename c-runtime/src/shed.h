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

extern struct ShedString empty_string;

extern void* shed_malloc(ShedSize, uint32_t alignment);

#endif
