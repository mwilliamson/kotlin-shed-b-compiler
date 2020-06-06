#include "./shed.h"

ShedValue const shed_unit = 0;

struct ShedString empty_string = { .length = 0, .data = {} };

ShedString alloc_string(uint64_t capacity) {
    return GC_malloc(sizeof(StringLength) + sizeof(uint8_t) * capacity);
}

// TODO: nested effects
jmp_buf shed_jmp_buf;

void shed_effect_handlers_discard() {
}

void shed_effect_handlers_call(EffectId effect_id, size_t operation_index, ShedValue* operation_arguments) {
    active_operation_arguments = operation_arguments;
    longjmp(shed_jmp_buf, 1 + operation_index);
}
