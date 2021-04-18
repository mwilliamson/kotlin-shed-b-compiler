#include <stdint.h>

#include "../deps/gc/include/gc.h"

#include "shed.h"

void* shed_malloc(ShedSize size, uint32_t alignment) {
    return GC_malloc(size);
}
