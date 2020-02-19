import io

from .. import Options


def unicode_scalar_to_hex_string(char):
    return format(ord(char), "X")


def unicode_scalar_to_int(char):
    return ord(char)


def unicode_scalar_to_string(char):
    return char


def unicode_scalar_count(string):
    return len(string)


def drop_left_unicode_scalars(count, string):
    return string[count:]


def next(string_slice):
    string, start_index, end_index = string_slice
    if start_index < end_index:
        result = (string[start_index], (string, start_index + 1, end_index))
        return Options.some(result)
    else:
        return Options.none


def replace(old, new, string):
    return string.replace(old, new)


def slice(string):
    return (string, 0, len(string))


def substring(start_index, end_index, value):
    return value[start_index:end_index]
