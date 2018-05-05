import io


def char_to_hex_string(char):
    return format(ord(char), "X")


def char_to_string(char):
    return char


def length(string):
    return len(string)


def map_characters(func, string):
    result = io.StringIO()
    for char in string:
        result.write(func(char))
    return result.getvalue()


def repeat(string, times):
    return string * times


def replace(old, new, string):
    return string.replace(old, new)
