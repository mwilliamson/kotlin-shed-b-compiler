import io

from .. import Options


def code_point_to_hex_string(char):
    return format(ord(char), "X")


def code_point_to_int(char):
    return ord(char)


def code_point_to_string(char):
    return char


def code_point_count(string):
    return len(string)


def index_at_code_point_count(count, value):
    return count


def last_index(value):
    return len(value)


def next(index, value):
    if index < len(value):
        return Options.some((value[index], index + 1))
    else:
        return Options.none


def replace(old, new, string):
    return string.replace(old, new)


def substring(start_index, end_index, value):
    return value[start_index:end_index]


zero_index = 0
