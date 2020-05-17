class _NonLocalReturn(Exception):
    def __init__(self, value):
        super().__init__("non local return")
        self.value = value


def non_local_return(value):
    raise _NonLocalReturn(value)


def run(func, on_non_local_return):
    try:
        return func()
    except _NonLocalReturn as error:
        return on_non_local_return(error.value)
