from .. import Options, Sequences


def list_to_sequence(elements):
    return _list_index_to_sequence(elements, 0)


def _list_index_to_sequence(elements, index):
    def next_item():
        if index < len(elements):
            return Sequences.SequenceItem(
                head=elements[index],
                tail=_list_index_to_sequence(elements, index + 1),
            )
        else:
            return Sequences.end
    
    return Sequences.Sequence(
        next=next_item,
    )


def sequence_to_list(sequence):
    result = []
    
    while True:
        item = sequence.next()
        if item._union_tag__stdlib__sequences__sequence_iterator == "Stdlib.Sequences.@SequenceEnd":
            return result
        else:
            result.append(item.head)
            sequence = item.tail
