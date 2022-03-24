#ifndef SHED_POLYFILLS_STRING_H
#define SHED_POLYFILLS_STRING_H

#include <stddef.h>

void *memcpy(void *dest, const void *src, size_t n);
int memcmp(const void *s1, const void *s2, size_t n);

#endif
