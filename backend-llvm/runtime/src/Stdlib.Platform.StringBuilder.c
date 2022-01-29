#include <string.h>

#include "../deps/gc/include/gc.h"

#include "./shed.h"
#include "./shed_platform_effects.h"
#include "./stringbuilder.h"

static EffectId effect_id = -10;

static ShedAny handle_write(struct EffectHandler* effect_handler, void* context, ShedString value) {
    string_builder_append(context, value->data, value->length);
    return shed_unit;
}

ShedString shed_module_fun__Stdlib__Platform__StringBuilder__build(ShedEnvironment env, struct ShedClosure* closure) {
    ShedSize initial_capacity = 16;

    struct StringBuilder string_builder;
    string_builder_init(&string_builder, initial_capacity);

    struct EffectHandler* effect_handler = shed_effects_push(effect_id, 1, 0);
    shed_effects_set_operation_handler(effect_handler, 0, handle_write, &string_builder);

    ShedAny (*func)(ShedEnvironment) = (ShedAny (*)(ShedEnvironment)) closure->function;
    func(closure->environment);

    shed_effects_discard();

    return string_builder_build(&string_builder);
}

ShedAny shed_module_fun__Stdlib__Platform__StringBuilder__write(ShedEnvironment env, ShedString value) {
    struct EffectHandler* effect_handler = shed_effects_find_effect_handler(effect_id);
    struct OperationHandler* operation_handler = shed_effects_get_operation_handler(effect_handler, 0);
    ShedAny (*function)(struct EffectHandler*, void*, ShedString) = operation_handler->function;
    return function(effect_handler, operation_handler->context, value);
}
