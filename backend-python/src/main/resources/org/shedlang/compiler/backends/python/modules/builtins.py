from functools import partial

class ShapeField(object):
    __slots__ = ("get", "name")

    def __init__(self, get, name):
        self.get = get
        self.name = name


def varargs(cons, nil):
    def f(*args):
        result = nil
        for arg in reversed(args):
            result = cons(arg, result)
        return result

    return f
