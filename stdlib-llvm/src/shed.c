#include <string.h>

#include "./shed.h"

ShedValue const shed_unit = 0;

struct ShedString empty_string = { .length = 0, .data = {} };

ShedString alloc_string(uint64_t capacity) {
    return GC_malloc(sizeof(StringLength) + sizeof(uint8_t) * capacity);
}

struct EffectHandler {
    EffectId effect_id;
    struct EffectHandler* next;
    jmp_buf env;
};

static struct EffectHandler* effect_handler_stack = NULL;

void shed_effect_handlers_discard() {
}

jmp_buf shed_jmp_buf;

void shed_effect_handlers_push(EffectId effect_id) {
    struct EffectHandler* effect_handler = GC_malloc(sizeof(struct EffectHandler));
    effect_handler->effect_id = effect_id;
    effect_handler->next = effect_handler_stack;
    memcpy(effect_handler->env, shed_jmp_buf, sizeof(jmp_buf));
    effect_handler_stack = effect_handler;
}

void shed_effect_handlers_call(EffectId effect_id, size_t operation_index, ShedValue* operation_arguments) {
    active_operation_arguments = operation_arguments;
    while (effect_handler_stack != NULL) {
        if (effect_handler_stack->effect_id == effect_id) {
            longjmp(effect_handler_stack->env, 1 + operation_index);
        } else {
            effect_handler_stack = effect_handler_stack->next;
        }
    }
}
