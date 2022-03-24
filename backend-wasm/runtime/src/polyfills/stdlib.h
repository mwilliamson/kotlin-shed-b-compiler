#ifndef SHED_POLYFILLS_STDLIB_H
#define SHED_POLYFILLS_STDLIB_H

typedef unsigned long size_t;

void* malloc(size_t size);
void* realloc(void* ptr, size_t size);
void free(void* ptr);

#endif
