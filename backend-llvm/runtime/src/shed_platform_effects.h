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
    ShedAny state;
    struct OperationHandler operation_handlers[];
};

void shed_effects_discard();

struct EffectHandler* shed_effects_push_alwaysresume_nostate(
    EffectId effect_id,
    OperationIndex operation_count
);
struct EffectHandler* shed_effects_push(
    EffectId effect_id,
    OperationIndex operation_count,
    jmp_buf* env,
    ShedAny initial_state
);
void shed_effects_set_operation_handler(
    struct EffectHandler* effect_handler,
    OperationIndex operation_index,
    void* function,
    void* context
);
struct EffectHandler* shed_effects_find_effect_handler(EffectId effect_id);
struct OperationHandler* shed_effects_get_operation_handler(
    struct EffectHandler* effect_handler,
    OperationIndex operation_index
);
void* shed_effects_operation_handler_get_function(
    struct OperationHandler* operation_handler
);
void* shed_effects_operation_handler_get_context(
    struct OperationHandler* operation_handler
);

ShedAny shed_operation_handler_exit(struct EffectHandler* effect_handler);

extern ShedAny shed_operation_handler_value;

#endif
