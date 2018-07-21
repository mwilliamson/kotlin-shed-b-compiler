from __future__ import print_function

from functools import partial

int_to_string = str

_list = list
def list(*args):
    return _list(args)

_print = print
def print(value):
    _print(value, end="")


def symbol_factory():
    symbols = {}

    def create_symbol(name):
        if name not in symbols:
            symbols[name] = object()

        return symbols[name]

    return create_symbol
