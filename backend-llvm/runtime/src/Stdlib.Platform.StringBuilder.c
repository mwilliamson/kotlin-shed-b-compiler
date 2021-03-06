#include <string.h>

#include "../deps/gc/include/gc.h"

#include "./effects.h"
#include "./shed.h"
#include "./stringbuilder.h"

static EffectId effect_id = -10;

static ShedAny handle_write(struct EffectHandler* effect_handler, void* context, ShedAny* operation_arguments) {
    ShedString value = *(ShedString*)operation_arguments;
    string_builder_append(context, value->data, value->length);
    return shed_unit;
}

ShedString shed_module_fun__Stdlib__Platform__StringBuilder__build(ShedEnvironment env, struct ShedClosure* closure) {
    ShedSize initial_capacity = 16;

    struct StringBuilder string_builder;
    string_builder_init(&string_builder, initial_capacity);

    struct EffectHandler* effect_handler = shed_effect_handlers_push(effect_id, 1, 0);
    shed_effect_handlers_set_operation_handler(effect_handler, 0, handle_write, &string_builder);

    ShedAny (*func)(ShedEnvironment) = (ShedAny (*)(ShedEnvironment)) closure->function;
    func(closure->environment);

    shed_effect_handlers_discard();

    return string_builder_build(&string_builder);
}

ShedAny shed_module_fun__Stdlib__Platform__StringBuilder__write(ShedEnvironment env, ShedString value) {
    return shed_effect_handlers_call(effect_id, 0, (ShedAny*)&value);
}
