#include "./shed.h"

#include "../../../c-bindings/Core.Options.h"

ShedAny shed_module_fun__Core__Cast__cast(ShedEnvironment env, ShedMetaType type, ShedCastable value) {
    if (type->typeTagValue == value->tagValue) {
        struct ShedClosure* someClosure = (struct ShedClosure*) shed__module_value__Core_Options.some;
        ShedAny (*someFunction)(ShedEnvironment, ShedCastable) = (ShedAny (*)(ShedEnvironment, ShedCastable)) someClosure->function;
        return someFunction(&someClosure->environment[0], value);
    } else {
        return shed__module_value__Core_Options.none;
    }
}
