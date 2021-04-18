#ifndef SHED_H
#define SHED_H

#include "shed_platform.h"

struct ShedString {
    ShedSize length;
    uint8_t data[];
};

typedef struct ShedString* ShedString;

extern void* shed_malloc(ShedSize, uint32_t alignment);

#endif
