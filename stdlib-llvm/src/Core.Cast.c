#include "./shed.h"

#include "../../c-bindings/Core.Options.h"

ShedValue Shed_Core_Cast_cast(ShedEnvironment env, ShedMetaType type, ShedCastable value) {
    if (type->typeTagValue == value->tagValue) {
        struct ShedClosure* someClosure = (struct ShedClosure*) shed__module_value__Core_Options.some;
        ShedValue (*someFunction)(ShedEnvironment, ShedCastable) = (ShedValue (*)(ShedEnvironment, ShedCastable)) someClosure->function;
        return someFunction(&someClosure->environment[0], value);
    } else {
        return shed__module_value__Core_Options.none;
    }
}
