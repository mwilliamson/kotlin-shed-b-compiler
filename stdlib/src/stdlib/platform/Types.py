from .. import Options

def cast(type, value):
    if isinstance(value, type):
        return Options.some(value)
    else:
        return Options.none


def name(type):
    return type.__name__


def type_of(value):
    return type(value)
