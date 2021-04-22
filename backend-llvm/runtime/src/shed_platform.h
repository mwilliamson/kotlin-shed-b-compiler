#ifndef SHED_PLATFORM_H
#define SHED_PLATFORM_H

#include <stdint.h>
#include <setjmp.h>

#include "../deps/gc/include/gc.h"

extern int shed__argc;
extern char** shed__argv;

typedef uint64_t ShedAny;
typedef uint64_t ShedBool;
typedef int64_t ShedInt;
typedef uint64_t ShedUnicodeScalar;

extern ShedAny const shed_unit;

typedef uint64_t ShedSize;

typedef uint64_t ShedTagValue;

#endif
