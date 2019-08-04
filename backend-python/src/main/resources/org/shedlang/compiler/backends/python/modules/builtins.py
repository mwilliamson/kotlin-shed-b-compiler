from functools import partial

class ShapeField(object):
    __slots__ = ("get", "name")

    def __init__(self, get, name):
        self.get = get
        self.name = name


_list = list
def list(*args):
    return _list(args)

_print = print
def print(value):
    _print(value, end="")
