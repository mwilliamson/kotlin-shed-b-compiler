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

ShedValue shed_handle_computational_effect(struct EffectHandler* effect_handler, size_t operation_index, ShedValue* operation_arguments) {
    active_operation_arguments = operation_arguments;
    effect_handler_stack = effect_handler->next;
    longjmp(*(jmp_buf*)effect_handler->context, 1 + operation_index);
}

void shed_effect_handlers_push_effect_handler(
    EffectId effect_id,
    ShedValue (*handle)(struct EffectHandler* effect_handler, size_t operation_index, ShedValue* operation_arguments),
    void* context
) {
    struct EffectHandler* effect_handler = GC_malloc(sizeof(struct EffectHandler));
    effect_handler->effect_id = effect_id;
    effect_handler->next = effect_handler_stack;
    effect_handler->handle = handle;
    effect_handler->context = context;
    effect_handler_stack = effect_handler;
}

jmp_buf* alloc_jmp_buf() {
    return GC_malloc(sizeof(jmp_buf));
}

void shed_effect_handlers_push(EffectId effect_id, jmp_buf* env) {
    shed_effect_handlers_push_effect_handler(effect_id, &shed_handle_computational_effect, env);
}

ShedValue shed_effect_handlers_call(EffectId effect_id, size_t operation_index, ShedValue* operation_arguments) {
    struct EffectHandler* effect_handler = effect_handler_stack;
    while (effect_handler != NULL) {
        if (effect_handler->effect_id == effect_id) {
            return effect_handler->handle(effect_handler, operation_index, operation_arguments);
        } else {
            effect_handler = effect_handler->next;
        }
    }
    return shed_unit;
}
