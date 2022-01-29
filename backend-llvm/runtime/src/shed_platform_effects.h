#ifndef SHED_PLATFORM_EFFECTS_H
#define SHED_PLATFORM_EFFECTS_H

#include <setjmp.h>

#include "./shed.h"

struct OperationHandler {
    void* function;
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
    void* function,
    void* context
);
struct EffectHandler* shed_effect_handlers_find_effect_handler(EffectId effect_id);
struct OperationHandler* shed_effect_handlers_get_operation_handler(
    struct EffectHandler* effect_handler,
    OperationIndex operation_index
);
void* shed_effect_handlers_operation_handler_get_function(
    struct OperationHandler* operation_handler
);
void* shed_effect_handlers_operation_handler_get_context(
    struct OperationHandler* operation_handler
);

ShedAny shed_operation_handler_exit(ShedAny exit_value);

extern ShedAny shed_exit_value;

#endif
