#include <stdint.h>

#include "string.h"

void *memcpy(void *dest, const void *src, size_t n) {
    for (size_t i = 0; i < n; i++) {
        ((uint8_t*)dest)[i] = ((const uint8_t*)src)[i];
    }
    return dest;
}

int memcmp(const void *s1, const void *s2, size_t n) {
    const unsigned char* bytes1 = (const unsigned char*) s1;
    const unsigned char* bytes2 = (const unsigned char*) s2;
    for (size_t i = 0; i < n; i++) {
        int result = bytes1[i] - bytes2[i];
        if (result != 0) {
            return result;
        }
    }
    return 0;
}