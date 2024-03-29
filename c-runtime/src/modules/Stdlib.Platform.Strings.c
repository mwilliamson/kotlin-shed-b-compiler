#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "../../../backend-llvm/runtime/deps/utf8proc/utf8proc.h"

#include "../shed.h"
#include "../strings.h"
#include "../stringbuilder.h"

#include "../../../c-bindings/Core.Options.h"

static bool isContinuationByte(uint8_t byte) {
    return (byte & 0xc0) == 0x80;
}

static utf8proc_size_t unicodeScalarCountToIndex(ShedInt count, ShedString string) {
    ShedSize length = string->length;
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
        ShedSize length = endIndex - startIndex;
        ShedString result = shed_string_alloc(length);
        result->length = length;
        memcpy(result->data, &string->data[startIndex], length);
        return result;
    } else {
        return &empty_string;
    }
}

ShedString shed_module_fun__Stdlib__Platform__Strings__dropLeftUnicodeScalars(ShedEnvironment env, ShedInt toDrop, ShedString string) {
    utf8proc_size_t startIndex = unicodeScalarCountToIndex(toDrop, string);
    return substringAtIndices(startIndex, string->length, string);
}

ShedAny shed_module_fun__Stdlib__Platform__Strings__next(ShedEnvironment env, ShedStringSlice slice) {
    if (slice->startIndex < slice->endIndex) {
        utf8proc_int32_t scalar;

        utf8proc_ssize_t bytesRead = utf8proc_iterate(
            &slice->string->data[slice->startIndex],
            slice->endIndex - slice->startIndex,
            &scalar
        );

        ShedStringSlice newSlice = shed_malloc(sizeof(struct ShedStringSlice), 4);
        newSlice->string = slice->string;
        newSlice->startIndex = slice->startIndex + bytesRead;
        newSlice->endIndex = slice->endIndex;

        ShedAny* result = shed_malloc(sizeof(ShedAny) * 2, 4);
        result[0] = scalar;
        result[1] = (ShedAny) newSlice;

        struct ShedClosure* someClosure = (struct ShedClosure*) shed__module_value__Core_Options.some;
        ShedAny (*someFunction)(ShedEnvironment, ShedAny) = (ShedAny (*)(ShedEnvironment, ShedAny)) someClosure->function;
        return someFunction(&someClosure->environment[0], (ShedAny)result);
    } else {
        return shed__module_value__Core_Options.none;
    }
}

ShedString shed_module_fun__Stdlib__Platform__Strings__replace(ShedEnvironment env, ShedString old, ShedString new, ShedString string) {
    // TODO: handle non-ASCII characters
    // TODO: handle zero-length old
    ShedSize old_length = old->length;
    ShedSize new_length = new->length;
    ShedSize string_length = string->length;

    struct StringBuilder string_builder;
    string_builder_init(&string_builder, string_length);

    ShedSize string_index = 0;
    ShedSize last_index = 0;

    while (string_index + old_length <= string_length) {
        if (memcmp(&string->data[string_index], old->data, old_length) == 0) {
            string_builder_append(&string_builder, &string->data[last_index], string_index - last_index);
            string_builder_append(&string_builder, new->data, new_length);
            string_index += old_length;
            last_index = string_index;
        } else {
            string_index++;
        }
    }
    string_builder_append(&string_builder, &string->data[last_index], string_length - last_index);

    return string_builder_build(&string_builder);
}

ShedStringSlice shed_module_fun__Stdlib__Platform__Strings__slice(ShedEnvironment env, ShedString string) {
    ShedStringSlice slice = shed_malloc(sizeof(struct ShedStringSlice), 4);
    slice->string = string;
    slice->startIndex = 0;
    slice->endIndex = string->length;
    return slice;
}

ShedString shed_module_fun__Stdlib__Platform__Strings__substring(ShedEnvironment env, ShedInt startCount, ShedInt endCount, ShedString string) {
    utf8proc_size_t startIndex = unicodeScalarCountToIndex(startCount, string);
    utf8proc_size_t endIndex = unicodeScalarCountToIndex(endCount, string);
    return substringAtIndices(startIndex, endIndex, string);
}

ShedString shed_module_fun__Stdlib__Platform__Strings__unicodeScalarToString(ShedEnvironment env, ShedUnicodeScalar scalar) {
    ShedString string = shed_string_alloc(4);
    string->length = utf8proc_encode_char(scalar, &string->data[0]);
    return string;
}

ShedInt shed_module_fun__Stdlib__Platform__Strings__unicodeScalarCount(ShedEnvironment env, ShedString string) {
    ShedSize length = string->length;
    long count = 0;
    for (ShedSize index = 0; index < length; index++) {
        if (!isContinuationByte(string->data[index])) {
            count++;
        }
    }
    return count;
}

ShedInt shed_module_fun__Stdlib__Platform__Strings__unicodeScalarToInt(ShedEnvironment env, ShedUnicodeScalar scalar) {
    return scalar;
}

ShedString shed_module_fun__Stdlib__Platform__Strings__unicodeScalarToHexString(ShedEnvironment env, ShedUnicodeScalar scalar) {
    if (scalar == 0) {
        ShedString string = shed_string_alloc(1);
        string->length = 1;
        string->data[0] = '0';
        return string;
    } else {
        ShedSize length = 0;

        ShedUnicodeScalar remaining = scalar;
        while (remaining != 0) {
            length += 1;
            remaining = remaining >> 4;
        }

        ShedString string = shed_string_alloc(length);
        string->length = length;

        ShedSize index = length;
        remaining = scalar;
        while (remaining != 0) {
            index -= 1;
            string->data[index] = "0123456789ABCDEF"[remaining & 0xf];
            remaining = remaining >> 4;
        }

        return string;
    }
}
