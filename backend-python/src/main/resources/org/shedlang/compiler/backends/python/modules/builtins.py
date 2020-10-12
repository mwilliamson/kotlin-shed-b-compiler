from functools import partial

class ShapeField(object):
    __slots__ = ("get", "name", "update")

    def __init__(self, get, name, update):
        self.get = get
        self.name = name
        self.update = update


def varargs(cons, nil):
    def f(*args):
        result = nil
        for arg in reversed(args):
            result = cons(arg, result)
        return result

    return f


class _EffectHandler(object):
    __slots__ = ("effect", "operation_handlers", "next", "Exit", "child_state")

    def __init__(self, effect, operation_handlers, next, Exit, child_state):
        self.effect = effect
        self.operation_handlers = operation_handlers
        self.next = next
        self.Exit = Exit
        self.child_state = child_state

    def exit(self, value):
        raise self.Exit(value)


_undefined = object()


_effect_handler_stack = _EffectHandler(
    effect=None,
    operation_handlers=None,
    next=None,
    Exit=None,
    child_state=_undefined,
)


def effect_handler_set_state(state):
    _effect_handler_stack.child_state = state


def effect_handler_push(effect, operation_handlers):
    global _effect_handler_stack

    class Exit(Exception):
        def __init__(self, value):
            self.value = value

    _effect_handler_stack.Exit = Exit

    effect_handler = _EffectHandler(
        effect=effect,
        operation_handlers=operation_handlers,
        next=_effect_handler_stack,
        Exit=None,
        child_state=_undefined,
    )

    _effect_handler_stack = effect_handler

    return effect_handler.next


def effect_handler_discard():
    global _effect_handler_stack

    _effect_handler_stack = _effect_handler_stack.next


def effect_handler_call(effect_id, operation_name, *args, **kwargs):
    effect_handler = _effect_handler_stack
    while effect_handler.effect._effect_id != effect_id:
        effect_handler = effect_handler.next

    return effect_handler.operation_handlers[operation_name](*args, **kwargs)


def effect_handler_exit(value):
    _effect_handler_stack.exit(value)


def effect_handler_create_operation_handler(handler):
    effect_handler = _effect_handler_stack

    def handle(*args, **kwargs):
        global _effect_handler_stack

        old_effect_handler_stack = _effect_handler_stack
        _effect_handler_stack = effect_handler

        if effect_handler.child_state is not _undefined:
            args = (effect_handler.child_state, ) + args

        value = handler(*args, **kwargs)

        _effect_handler_stack = old_effect_handler_stack

        return value

    return handle
