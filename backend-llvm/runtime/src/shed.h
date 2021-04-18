#ifndef SHED_H
#define SHED_H

#include <stdint.h>
#include <setjmp.h>

#include "../deps/gc/include/gc.h"

extern int shed__argc;
extern char** shed__argv;

typedef uint64_t ShedUnicodeScalar;
typedef int64_t ShedInt;
typedef uint64_t ShedAny;

extern ShedAny const shed_unit;

typedef uint64_t ShedSize;
struct ShedString {
    ShedSize length;
    uint8_t data[];
};

extern struct ShedString empty_string;

typedef struct ShedString* ShedString;

struct ShedStringSlice {
    ShedString string;
    ShedSize startIndex;
    ShedSize endIndex;
};

typedef struct ShedStringSlice* ShedStringSlice;

ShedString alloc_string(uint64_t capacity);

typedef ShedAny* ShedEnvironment;

struct ShedClosure {
    void* function;
    ShedAny environment[];
};

typedef uint64_t ShedTagValue;

struct ShedCastable {
    ShedTagValue tagValue;
};

typedef struct ShedCastable* ShedCastable;

struct ShedMetaType {
    void* constructor;
    ShedTagValue typeTagValue;
};

typedef struct ShedMetaType* ShedMetaType;

#endif
