from .. import Options, Sequences


def list_to_sequence(elements):
    return _list_index_to_sequence(elements, 0)


def _list_index_to_sequence(elements, index):
    def next_item():
        if index < len(elements):
            return Options.some(
                Sequences.SequenceItem(
                    head=elements[index],
                    tail=_list_index_to_sequence(elements, index + 1),
                )
            )
        else:
            return Options.none
    
    return Sequences.Sequence(
        next=next_item,
    )


def sequence_to_list(sequence):
    result = []
    
    while True:
        item = sequence.next()
        if isinstance(item, Options.None_):
            return result
        else:
            result.append(item.value.head)
            sequence = item.value.tail
