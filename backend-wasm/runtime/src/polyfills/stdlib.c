#include "shed.h"
#include "stdlib.h"
#include "string.h"

void* malloc(size_t size) {
    return shed_malloc(size, 4);
}

void* realloc(void* ptr, size_t size) {
    void* new_ptr = malloc(size);
    memcpy(new_ptr, ptr, size);
    free(ptr);
    return new_ptr;
}

void free(void* ptr) {
}
