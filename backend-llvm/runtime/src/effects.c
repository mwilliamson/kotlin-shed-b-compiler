#include <string.h>

#include "./effects.h"

static struct EffectHandler default_effect_handler = {
    .effect_id = -1
};

static struct EffectHandler* effect_handler_stack = &default_effect_handler;

void shed_effect_handlers_discard() {
    effect_handler_stack = effect_handler_stack->next;
}

ShedAny shed_operation_handler_exit(ShedAny exit_value) {
    shed_exit_value = exit_value;
    longjmp(*effect_handler_stack->exit_env, 1);
}

void shed_effect_handlers_set_state(ShedAny state) {
    effect_handler_stack->child_state = state;
}

ShedAny shed_effect_handlers_get_state(void) {
    return effect_handler_stack->child_state;
}

struct EffectHandler* shed_effect_handlers_push(
    EffectId effect_id,
    OperationIndex operation_count,
    jmp_buf* env
) {
    effect_handler_stack->exit_env = env;

    struct EffectHandler* effect_handler = GC_malloc(sizeof(struct EffectHandler) + operation_count * sizeof(struct OperationHandler));
    effect_handler->effect_id = effect_id;
    effect_handler->next = effect_handler_stack;
    effect_handler->exit_env = 0;
    effect_handler->child_state = shed_unit;
    effect_handler_stack = effect_handler;
    return effect_handler;
}

void shed_effect_handlers_set_operation_handler(
    struct EffectHandler* effect_handler,
    OperationIndex operation_index,
    OperationHandlerFunction* function,
    void* context
) {
    effect_handler->operation_handlers[operation_index].function = function;
    effect_handler->operation_handlers[operation_index].context = context;
}

ShedAny shed_effect_handlers_call(EffectId effect_id, OperationIndex operation_index, ShedAny* operation_arguments) {
    struct EffectHandler* effect_handler = effect_handler_stack;
    while (effect_handler != NULL) {
        if (effect_handler->effect_id == effect_id) {
            struct OperationHandler* operation_handler = &effect_handler->operation_handlers[operation_index];
            return operation_handler->function(effect_handler, operation_handler->context, operation_arguments);
        } else {
            effect_handler = effect_handler->next;
        }
    }
    return shed_unit;
}

struct EffectHandler* shed_effect_handlers_enter(struct EffectHandler* effect_handler) {
    struct EffectHandler* previous_stack = effect_handler_stack;
    effect_handler_stack = effect_handler->next;
    return previous_stack;
}

void shed_effect_handlers_restore(struct EffectHandler* effect_handler) {
    effect_handler_stack = effect_handler;
}
