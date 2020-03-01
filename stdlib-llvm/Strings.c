#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

typedef uint64_t ShedUnicodeScalar;
typedef int64_t ShedInt;
typedef uint64_t ShedValue;

typedef uint64_t StringLength;
struct ShedString {
    StringLength length;
    uint8_t data[];
};

struct ShedString empty_string = { .length = 0, .data = {} };

typedef struct ShedString* ShedString;

struct ShedStringSlice {
    ShedString string;
    StringLength startIndex;
    StringLength endIndex;
};

typedef struct ShedStringSlice* ShedStringSlice;

ShedString alloc_string(uint64_t capacity) {
    return malloc(sizeof(StringLength) + sizeof(uint8_t) * capacity);
}

typedef ShedValue* ShedEnvironment;

struct ShedClosure {
    void* function;
    ShedValue environment[];
};

extern ShedValue shed__module_value__Core_Options[5];

ShedValue Shed_Stdlib_Platform_Strings_next(ShedEnvironment env, ShedStringSlice slice) {
    // TODO: handle non-ASCII characters
    if (slice->startIndex < slice->endIndex) {
        ShedUnicodeScalar scalar = slice->string->data[slice->startIndex];

        ShedStringSlice newSlice = malloc(sizeof(struct ShedStringSlice));
        newSlice->string = slice->string;
        newSlice->startIndex = slice->startIndex + 1;
        newSlice->endIndex = slice->endIndex;

        ShedValue* result = malloc(sizeof(ShedValue) * 2);
        result[0] = scalar;
        result[1] = (ShedValue) newSlice;

        struct ShedClosure* someClosure = (struct ShedClosure*) shed__module_value__Core_Options[4];
        ShedValue (*someFunction)(ShedEnvironment, ShedValue) = (ShedValue (*)(ShedEnvironment, ShedValue)) someClosure->function;
        return someFunction(&someClosure->environment[0], (ShedValue)result);
    } else {
        return shed__module_value__Core_Options[3];
    }
}

ShedString Shed_Stdlib_Platform_Strings_replace(ShedEnvironment env, ShedString old, ShedString new, ShedString string) {
    // TODO: handle non-ASCII characters
    // TODO: handle zero-length old
    StringLength oldLength = old->length;
    StringLength newLength = new->length;
    StringLength stringLength = string->length;
    StringLength capacity = new->length <= old->length
        ? stringLength
        : (new->length / oldLength + 1) * stringLength;
    ShedString result = alloc_string(capacity);

    StringLength stringIndex = 0;
    StringLength resultIndex = 0;
    while (stringIndex < stringLength) {
        bool isMatch = true;
        for (StringLength oldIndex = 0; oldIndex < oldLength; oldIndex++) {
            if (!(stringIndex + oldIndex < stringLength && string->data[stringIndex + oldIndex] == old->data[oldIndex])) {
                isMatch = false;
                break;
            }
        }

        if (isMatch) {
            for (StringLength newIndex = 0; newIndex < newLength; newIndex++) {
                result->data[resultIndex++] = new->data[newIndex];
            }
            stringIndex += oldLength;
        } else {
            result->data[resultIndex++] = string->data[stringIndex];
            stringIndex++;
        }
    }

    result->length = resultIndex;

    return result;
}

ShedStringSlice Shed_Stdlib_Platform_Strings_slice(ShedEnvironment env, ShedString string) {
    ShedStringSlice slice = malloc(sizeof(struct ShedStringSlice));
    slice->string = string;
    slice->startIndex = 0;
    slice->endIndex = string->length;
    return slice;
}

ShedString Shed_Stdlib_Platform_Strings_substring(ShedEnvironment env, ShedInt startIndex, ShedInt endIndex, ShedString string) {
    // TODO: handle non-ASCII characters
    if (startIndex < 0) {
        startIndex = string->length + startIndex;
    }
    if (endIndex < 0) {
        endIndex = string->length + endIndex;
    }

    if (endIndex > string->length) {
        endIndex = string->length;
    }

    if (startIndex < endIndex) {
        StringLength length = endIndex - startIndex;
        ShedString result = alloc_string(length);
        result->length = length;
        memcpy(result->data, &string->data[startIndex], length);
        return result;
    } else {
        return &empty_string;
    }
}

ShedString Shed_Stdlib_Platform_Strings_unicodeScalarToString(ShedEnvironment env, ShedUnicodeScalar scalar) {
    // TODO: handle non-ASCII characters
    ShedString string = alloc_string(1);
    string->length = 1;
    string->data[0] = scalar;
    return string;
}

ShedInt Shed_Stdlib_Platform_Strings_unicodeScalarCount(ShedEnvironment env, ShedString string) {
    // TODO: handle non-ASCII characters
    return string->length;
}

ShedInt Shed_Stdlib_Platform_Strings_unicodeScalarToInt(ShedEnvironment env, ShedUnicodeScalar scalar) {
    return scalar;
}

ShedString Shed_Stdlib_Platform_Strings_unicodeScalarToHexString(ShedEnvironment env, ShedUnicodeScalar scalar) {
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

ShedString Shed_Stdlib_Platform_Strings_dropLeftUnicodeScalars(ShedEnvironment env, ShedInt toDrop, ShedString string) {
    return Shed_Stdlib_Platform_Strings_substring(NULL, toDrop, string->length, string);
}
