from ...builtins import effect_handler_call, effect_handler_discard, effect_handler_push


class _StringBuilderEffect(object):
    _effect_id = -10
    _Exit = None


def build(func):
    string_builder = []

    def write(value):
        string_builder.append(value)

    effect_handler_push(_StringBuilderEffect, dict(
        write=write,
    ))

    func()

    effect_handler_discard()

    return "".join(string_builder)


def write(value):
    effect_handler_call(_StringBuilderEffect._effect_id, "write", value)
