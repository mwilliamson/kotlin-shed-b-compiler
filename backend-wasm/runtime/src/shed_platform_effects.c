#include <stddef.h>

#include "./shed_platform_effects.h"

static struct EffectHandler default_effect_handler = {
    .effect_id = -1
};

static struct EffectHandler* effect_handler_stack = &default_effect_handler;

struct EffectHandler* shed_effects_push_alwaysresume_nostate(
    EffectId effect_id,
    OperationIndex operation_count
) {
    return shed_effects_push(effect_id, operation_count, shed_unit);
}

struct EffectHandler* shed_effects_push(
    EffectId effect_id,
    OperationIndex operation_count,
    ShedAny initial_state
) {
    struct EffectHandler* effect_handler = shed_malloc(sizeof(struct EffectHandler) + operation_count * sizeof(struct OperationHandler), 8);
    effect_handler->effect_id = effect_id;
    effect_handler->next = effect_handler_stack;
    effect_handler->state = initial_state;
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

struct EffectHandler* shed_effects_enter(struct EffectHandler* effect_handler) {
    struct EffectHandler* previous_stack = effect_handler_stack;
    effect_handler_stack = effect_handler->next;
    return previous_stack;
}

void shed_effects_restore(struct EffectHandler* effect_handler) {
    effect_handler_stack = effect_handler;
}

void shed_effects_discard() {
    effect_handler_stack = effect_handler_stack->next;
}

void shed_effects_set_state(struct EffectHandler* effect_handler, ShedAny state) {
    effect_handler->state = state;
}

ShedAny shed_effects_get_state(struct EffectHandler* effect_handler) {
    return effect_handler->state;
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
