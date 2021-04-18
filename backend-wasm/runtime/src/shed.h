#ifndef SHED_H
#define SHED_H

#include <stdint.h>

typedef uint32_t ShedValue;

typedef uint32_t StringLength;
struct ShedString {
    StringLength length;
    uint8_t data[];
};
typedef struct ShedString* ShedString;

#endif
