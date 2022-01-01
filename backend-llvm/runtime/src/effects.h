#ifndef SHED_EFFECTS_H
#define SHED_EFFECTS_H

#include <stdint.h>
#include <setjmp.h>

#include "./shed.h"

typedef uint32_t EffectId;
typedef size_t OperationIndex;

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

extern ShedAny shed_exit_value;

#endif
