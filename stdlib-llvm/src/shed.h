#ifndef SHED_H
#define SHED_H

#include <stdint.h>
#include <setjmp.h>

#include "../deps/gc/include/gc.h"

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

typedef uint32_t EffectId;

ShedValue* active_operation_arguments;

struct EffectHandler {
    EffectId effect_id;
    struct EffectHandler* next;
    ShedValue (*handle)(struct EffectHandler* effect_handler, size_t operation_index, ShedValue* operation_arguments);
    void* context;
};

void shed_effect_handlers_discard();
void shed_effect_handlers_push(EffectId effect_id, jmp_buf* env);
void shed_effect_handlers_push_effect_handler(
    EffectId effect_id,
    ShedValue (*handle)(struct EffectHandler* effect_handler, size_t operation_index, ShedValue* operation_arguments),
    void* context
);
ShedValue shed_effect_handlers_call(EffectId effect_id, size_t operation_index, ShedValue* operation_arguments);

#endif
