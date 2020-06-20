#include <string.h>

#include "./shed.h"

ShedValue const shed_unit = 0;

struct ShedString empty_string = { .length = 0, .data = {} };

ShedString alloc_string(uint64_t capacity) {
    return GC_malloc(sizeof(StringLength) + sizeof(uint8_t) * capacity);
}

static struct EffectHandler* effect_handler_stack = NULL;

void shed_effect_handlers_discard() {
    effect_handler_stack = effect_handler_stack->next;
}

ShedValue shed_operation_handler_exit(
    struct EffectHandler* effect_handler,
    ShedValue exit_value
) {
    shed_exit_value = exit_value;
    longjmp(*effect_handler->env, 1);
}

struct EffectHandler* shed_effect_handlers_push(
    EffectId effect_id,
    OperationIndex operation_count,
    jmp_buf* env
) {
    struct EffectHandler* effect_handler = GC_malloc(sizeof(struct EffectHandler) + operation_count * sizeof(struct OperationHandler));
    effect_handler->effect_id = effect_id;
    effect_handler->next = effect_handler_stack;
    effect_handler->env = env;
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

ShedValue shed_effect_handlers_call(EffectId effect_id, OperationIndex operation_index, ShedValue* operation_arguments) {
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
