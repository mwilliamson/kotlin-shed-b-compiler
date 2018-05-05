import io


def char_to_string(char):
    return char


def map_characters(func, string):
    result = io.StringIO()
    for char in string:
        result.write(func(char))
    return result.getvalue()


def replace(old, new, string):
    return string.replace(old, new)
