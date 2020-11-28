#ifndef SHED_H
#define SHED_H

#include <stdint.h>
#include <setjmp.h>

#include "../deps/gc/include/gc.h"

extern int shed__argc;
extern char** shed__argv;

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

ShedValue shed_exit_value;

struct EffectHandler;

typedef ShedValue (OperationHandlerFunction)(
    struct EffectHandler* effect_handler,
    void* context,
    ShedValue* operation_arguments
);

struct OperationHandler {
    OperationHandlerFunction* function;
    void* context;
};

struct EffectHandler {
    EffectId effect_id;
    struct EffectHandler* next;
    jmp_buf* exit_env;
    ShedValue child_state;
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
ShedValue shed_effect_handlers_call(EffectId effect_id, OperationIndex operation_index, ShedValue* operation_arguments);

ShedValue shed_operation_handler_exit(ShedValue exit_value);
#endif
