import io

from .. import Options


def code_point_to_hex_string(char):
    return format(ord(char), "X")


def code_point_to_string(char):
    return char


def code_point_count(string):
    return len(string)


def first_code_point(string):
    if len(string) == 0:
        return Options.none
    else:
        return Options.some(string[0])


def map_code_points(func, string):
    result = io.StringIO()
    for char in string:
        result.write(func(char))
    return result.getvalue()


def repeat(string, times):
    return string * times


def replace(old, new, string):
    return string.replace(old, new)


def substring(start_index, end_index, value):
    return value[start_index:end_index]
