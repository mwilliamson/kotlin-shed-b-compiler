from __future__ import print_function

from functools import partial

int_to_string = str

_list = list
def list(*args):
    return _list(args)

_print = print
def print(value):
    _print(value, end="")

def for_each(func, elements):
    for element in elements:
        func(element)

def map(func, elements):
    result = []
    for element in elements:
        result.append(func(element))
    return result

def reduce(func, initial, elements):
    result = initial
    for element in elements:
        result = func(result, element)
    return result
