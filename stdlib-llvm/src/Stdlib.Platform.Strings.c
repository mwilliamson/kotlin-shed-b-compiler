#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "../deps/gc/include/gc.h"
#include "../deps/utf8proc/utf8proc.h"

#include "./shed.h"

extern ShedValue shed__module_value__Core_Options[5];

static bool isContinuationByte(uint8_t byte) {
    return (byte & 0xc0) == 0x80;
}

static utf8proc_size_t unicodeScalarCountToIndex(ShedInt count, ShedString string) {
    StringLength length = string->length;
    if (count >= 0) {
        utf8proc_ssize_t currentCount = -1;

        for (utf8proc_size_t index = 0; index < length; index++) {
            if (!isContinuationByte(string->data[index])) {
                currentCount++;
            }
            if (currentCount == count) {
                return index;
            }
        }

        return length;
    } else {
        utf8proc_ssize_t currentCount = 0;

        for (utf8proc_ssize_t index = length - 1; index >= 0; index--) {
            if (!isContinuationByte(string->data[index])) {
                currentCount--;
            }
            if (currentCount == count) {
                return index;
            }
        }

        return 0;
    }
}

static ShedString substringAtIndices(utf8proc_size_t startIndex, utf8proc_size_t endIndex, ShedString string) {
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

ShedString Shed_Stdlib_Platform_Strings_dropLeftUnicodeScalars(ShedEnvironment env, ShedInt toDrop, ShedString string) {
    utf8proc_size_t startIndex = unicodeScalarCountToIndex(toDrop, string);
    return substringAtIndices(startIndex, string->length, string);
}

ShedValue Shed_Stdlib_Platform_Strings_next(ShedEnvironment env, ShedStringSlice slice) {
    if (slice->startIndex < slice->endIndex) {
        utf8proc_int32_t scalar;

        utf8proc_ssize_t bytesRead = utf8proc_iterate(
            &slice->string->data[slice->startIndex],
            slice->endIndex - slice->startIndex,
            &scalar
        );

        ShedStringSlice newSlice = GC_malloc(sizeof(struct ShedStringSlice));
        newSlice->string = slice->string;
        newSlice->startIndex = slice->startIndex + bytesRead;
        newSlice->endIndex = slice->endIndex;

        ShedValue* result = GC_malloc(sizeof(ShedValue) * 2);
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
    ShedStringSlice slice = GC_malloc(sizeof(struct ShedStringSlice));
    slice->string = string;
    slice->startIndex = 0;
    slice->endIndex = string->length;
    return slice;
}

ShedString Shed_Stdlib_Platform_Strings_substring(ShedEnvironment env, ShedInt startCount, ShedInt endCount, ShedString string) {
    utf8proc_size_t startIndex = unicodeScalarCountToIndex(startCount, string);
    utf8proc_size_t endIndex = unicodeScalarCountToIndex(endCount, string);
    return substringAtIndices(startIndex, endIndex, string);
}

ShedString Shed_Stdlib_Platform_Strings_unicodeScalarToString(ShedEnvironment env, ShedUnicodeScalar scalar) {
    ShedString string = alloc_string(4);
    string->length = utf8proc_encode_char(scalar, &string->data[0]);
    return string;
}

ShedInt Shed_Stdlib_Platform_Strings_unicodeScalarCount(ShedEnvironment env, ShedString string) {
    StringLength length = string->length;
    long count = 0;
    for (StringLength index = 0; index < length; index++) {
        if (!isContinuationByte(string->data[index])) {
            count++;
        }
    }
    return count;
}

ShedInt Shed_Stdlib_Platform_Strings_unicodeScalarToInt(ShedEnvironment env, ShedUnicodeScalar scalar) {
    return scalar;
}

ShedString Shed_Stdlib_Platform_Strings_unicodeScalarToHexString(ShedEnvironment env, ShedUnicodeScalar scalar) {
    if (scalar == 0) {
        ShedString string = alloc_string(1);
        string->length = 1;
        string->data[0] = '0';
        return string;
    } else {
        StringLength length = 0;

        ShedUnicodeScalar remaining = scalar;
        while (remaining != 0) {
            length += 1;
            remaining = remaining >> 4;
        }

        ShedString string = alloc_string(length);
        string->length = length;

        StringLength index = length;
        remaining = scalar;
        while (remaining != 0) {
            index -= 1;
            string->data[index] = "0123456789ABCDEF"[remaining & 0xf];
            remaining = remaining >> 4;
        }

        return string;
    }
}
