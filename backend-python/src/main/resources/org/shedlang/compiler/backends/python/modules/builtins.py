from __future__ import print_function

from functools import partial

int_to_string = str

_list = list
def list(*args):
    return _list(args)

_print = print
def print(value):
    _print(value, end="")


_symbols = {}

def create_symbol(module, name):
    if (module, name) not in _symbols:
        _symbols[(module, name)] = object()

    return _symbols[(module, name)]
