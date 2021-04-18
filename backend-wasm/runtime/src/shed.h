#ifndef SHED_H
#define SHED_H

#include <stdint.h>

typedef uint32_t ShedAny;

typedef uint32_t ShedSize;
struct ShedString {
    ShedSize length;
    uint8_t data[];
};
typedef struct ShedString* ShedString;

extern void* shed_malloc(ShedSize, uint32_t alignment);

#endif
