#include <unistd.h>

#include "./shed.h"

ShedAny shed_module_fun__Core__Io__print(ShedEnvironment env, ShedString value) {
    write(STDOUT_FILENO, value->data, value->length);
    return shed_unit;
}
