#include <stdbool.h>
#include <unistd.h>

#include "./shed.h"

ShedAny shed_module_fun__Core__Io__print(ShedEnvironment env, ShedString value) {
    size_t total_bytes_written = 0;
    while (total_bytes_written < value->length) {
        ssize_t bytes_written = write(STDOUT_FILENO, &value->data[total_bytes_written], value->length - total_bytes_written);
        if (bytes_written == -1) {
            break;
        }
        total_bytes_written += bytes_written;
    }
    return shed_unit;
}
