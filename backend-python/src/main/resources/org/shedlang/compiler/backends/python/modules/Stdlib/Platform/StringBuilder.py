_string_builder_stack = []


def build(func):
    _string_builder_stack.append([])
    try:
        func()
    except:
        _string_builder_stack.pop()
        raise

    string_builder = _string_builder_stack.pop()
    return "".join(string_builder)


def write(value):
    _string_builder_stack[-1].append(value)
