from . import Options

def cast(type_, value):
    if type_._tag_value == value._tag_value:
        return Options.some(value)
    else:
        return Options.none
