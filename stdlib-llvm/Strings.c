#include <stdint.h>
#include <stdlib.h>
#include <string.h>

typedef uint64_t ShedUnicodeScalar;
typedef int64_t ShedInt;

typedef uint64_t StringLength;
struct ShedString {
    StringLength length;
    uint8_t data[];
};

struct ShedString empty_string = { .length = 0, .data = {} };

typedef struct ShedString* ShedString;

ShedString alloc_string(uint64_t capacity) {
    return malloc(sizeof(StringLength) + sizeof(uint8_t) * capacity);
}

ShedString Shed_Stdlib_Platform_Strings_substring(void* env, ShedInt startIndex, ShedInt endIndex, ShedString string) {
    // TODO: handle non-ASCII characters
    if (startIndex < endIndex) {
        StringLength length = endIndex > startIndex ? endIndex - startIndex : 0;
        ShedString result = alloc_string(length);
        result->length = length;
        memcpy(result->data, &string->data[startIndex], length);
        return result;
    } else {
        return &empty_string;
    }
}

ShedString Shed_Stdlib_Platform_Strings_unicodeScalarToString(void* env, ShedUnicodeScalar scalar) {
    // TODO: handle non-ASCII characters
    ShedString string = alloc_string(1);
    string->length = 1;
    string->data[0] = scalar;
    return string;
}

ShedInt Shed_Stdlib_Platform_Strings_unicodeScalarCount(void* env, ShedString string) {
    // TODO: handle non-ASCII characters
    return string->length;
}

ShedInt Shed_Stdlib_Platform_Strings_unicodeScalarToInt(void* env, ShedUnicodeScalar scalar) {
    return scalar;
}

ShedString Shed_Stdlib_Platform_Strings_unicodeScalarToHexString(void* env, ShedUnicodeScalar scalar) {
    ShedString string = alloc_string(16);

    if (scalar == 0) {
        string->length = 1;
        string->data[0] = '0';
    } else {
        StringLength length = 0;

        ShedUnicodeScalar remaining = scalar;
        while (remaining != 0) {
            length += 1;
            remaining = remaining >> 4;
        }

        StringLength index = length;
        remaining = scalar;
        while (remaining != 0) {
            index -= 1;
            string->data[index] = "0123456789ABCDEF"[remaining & 0xf];
            remaining = remaining >> 4;
        }

        string->length = length;
    }
    return string;
}
