#include <string.h>

#include "./shed_platform_effects.h"

static struct EffectHandler default_effect_handler = {
    .effect_id = -1
};

static struct EffectHandler* effect_handler_stack = &default_effect_handler;

struct ExitStack {
    jmp_buf* env;
    struct ExitStack* next;
};

static struct ExitStack* exit_stack = NULL;

void shed_effects_discard() {
    effect_handler_stack = effect_handler_stack->next;
}

ShedAny shed_operation_handler_exit(ShedAny exit_value) {
    shed_exit_value = exit_value;
    jmp_buf* env = exit_stack->env;
    exit_stack = exit_stack->next;
    longjmp(*env, 1);
}

void shed_effects_set_state(ShedAny state) {
    effect_handler_stack->child_state = state;
}

ShedAny shed_effects_get_state(void) {
    return effect_handler_stack->child_state;
}

struct EffectHandler* shed_effects_push(
    EffectId effect_id,
    OperationIndex operation_count,
    jmp_buf* env
) {
    struct EffectHandler* effect_handler = GC_malloc(sizeof(struct EffectHandler) + operation_count * sizeof(struct OperationHandler));
    effect_handler->effect_id = effect_id;
    effect_handler->next = effect_handler_stack;
    effect_handler->exit_env = env;
    effect_handler->child_state = shed_unit;
    effect_handler_stack = effect_handler;
    return effect_handler;
}

void shed_effects_set_operation_handler(
    struct EffectHandler* effect_handler,
    OperationIndex operation_index,
    void* function,
    void* context
) {
    effect_handler->operation_handlers[operation_index].function = function;
    effect_handler->operation_handlers[operation_index].context = context;
}

struct EffectHandler* shed_effects_find_effect_handler(EffectId effect_id) {
    struct EffectHandler* effect_handler = effect_handler_stack;
    while (effect_handler != NULL) {
        if (effect_handler->effect_id == effect_id) {
            return effect_handler;
        } else {
            effect_handler = effect_handler->next;
        }
    }
    return NULL;
}

struct OperationHandler* shed_effects_get_operation_handler(
    struct EffectHandler* effect_handler,
    OperationIndex operation_index
) {
    return &effect_handler->operation_handlers[operation_index];
}

void* shed_effects_operation_handler_get_function(
    struct OperationHandler* operation_handler
) {
    return operation_handler->function;
}

void* shed_effects_operation_handler_get_context(
    struct OperationHandler* operation_handler
) {
    return operation_handler->context;
}

struct EffectHandler* shed_effects_enter(struct EffectHandler* effect_handler) {
    struct EffectHandler* previous_stack = effect_handler_stack;
    effect_handler_stack = effect_handler->next;

    struct ExitStack* new_exit_stack = GC_malloc(sizeof(struct ExitStack));
    new_exit_stack->next = exit_stack;
    new_exit_stack->env = effect_handler->exit_env;
    exit_stack = new_exit_stack;

    return previous_stack;
}

void shed_effects_restore(struct EffectHandler* effect_handler) {
    effect_handler_stack = effect_handler;
    exit_stack = exit_stack->next;
}
