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

typedef uint32_t EffectId;
typedef size_t OperationIndex;

ShedAny shed_exit_value;

struct EffectHandler;

typedef ShedAny (OperationHandlerFunction)(
    struct EffectHandler* effect_handler,
    void* context,
    ShedAny* operation_arguments
);

struct OperationHandler {
    OperationHandlerFunction* function;
    void* context;
};

struct EffectHandler {
    EffectId effect_id;
    struct EffectHandler* next;
    jmp_buf* exit_env;
    ShedAny child_state;
    struct OperationHandler operation_handlers[];
};

void shed_effect_handlers_discard();
struct EffectHandler* shed_effect_handlers_push(
    EffectId effect_id,
    OperationIndex operation_count,
    jmp_buf* env
);
void shed_effect_handlers_set_operation_handler(
    struct EffectHandler* effect_handler,
    OperationIndex operation_index,
    OperationHandlerFunction* function,
    void* context
);
ShedAny shed_effect_handlers_call(EffectId effect_id, OperationIndex operation_index, ShedAny* operation_arguments);

ShedAny shed_operation_handler_exit(ShedAny exit_value);
#endif
