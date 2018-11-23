from __future__ import print_function

from functools import partial

_list = list
def list(*args):
    return _list(args)

_print = print
def print(value):
    _print(value, end="")
