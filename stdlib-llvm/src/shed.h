#ifndef SHED_H
#define SHED_H

#include <stdint.h>

#include "../gc-8.0.4/include/gc.h"

typedef uint64_t ShedUnicodeScalar;
typedef int64_t ShedInt;
typedef uint64_t ShedValue;

extern ShedValue const shed_unit;

typedef uint64_t StringLength;
struct ShedString {
    StringLength length;
    uint8_t data[];
};

extern struct ShedString empty_string;

typedef struct ShedString* ShedString;

struct ShedStringSlice {
    ShedString string;
    StringLength startIndex;
    StringLength endIndex;
};

typedef struct ShedStringSlice* ShedStringSlice;

ShedString alloc_string(uint64_t capacity);

typedef ShedValue* ShedEnvironment;

struct ShedClosure {
    void* function;
    ShedValue environment[];
};

#endif
