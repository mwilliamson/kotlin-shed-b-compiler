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
    OperationIndex operation_index,
    void* context,
    ShedValue* operation_arguments
) {
    active_operation_arguments = operation_arguments;
    effect_handler_stack = effect_handler->next;
    longjmp(*(jmp_buf*)context, 1 + operation_index);
}

//~ ShedValue shed_operation_handler_resume(struct EffectHandler* effect_handler, OperationIndex operation_index, ShedValue* operation_arguments) {
    //~ // TODO: test switching of effect handler stack
    //~ struct EffectHandler* previous_effect_handler_stack = effect_handler_stack;
    //~ effect_handler_stack = effect_handler->next;

    //~ struct UserDefinedEffectHandlerContext* context = effect_handler->context;
    //~ struct ShedClosure* closure = &context->operation_handlers[operation_index];

    //~ effect_handler_stack = previous_effect_handler_stack;
//~ }

struct EffectHandler* shed_effect_handlers_push(
    EffectId effect_id,
    OperationIndex operation_count
) {
    struct EffectHandler* effect_handler = GC_malloc(sizeof(struct EffectHandler) + operation_count * sizeof(struct OperationHandler));
    effect_handler->effect_id = effect_id;
    effect_handler->next = effect_handler_stack;
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
            return operation_handler->function(effect_handler, operation_index, operation_handler->context, operation_arguments);
        } else {
            effect_handler = effect_handler->next;
        }
    }
    return shed_unit;
}
