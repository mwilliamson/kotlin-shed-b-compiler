import sys

from ..Lists import cons, nil


def args():
    args = nil

    for arg in reversed(sys.argv[1:]):
        args = cons(arg, args)

    return args
