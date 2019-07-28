var Options = require("../Options");
var Sequences = require("../Sequences");

function listToSequence(elements) {
    return listIndexToSequence(elements, 0);
}

function listIndexToSequence(elements, index) {
    return Sequences.Sequence({
        next: function() {
            if (index < elements.length) {
                return Sequences.SequenceItem({
                    head: elements[index],
                    tail: listIndexToSequence(elements, index + 1)
                });
            } else {
                return Sequences.end;
            }
        }
    });
}

function sequenceToList(sequence) {
    var result = [];
    
    while (true) {
        var item = sequence.next();
        if (item._unionTag_Stdlib_Sequences_SequenceIterator === "Stdlib.Sequences.`SequenceEnd") {
            return result;
        } else {
            result.push(item.head);
            sequence = item.tail;
        }
    }
}

exports.listToSequence = listToSequence;
exports.sequenceToList = sequenceToList;
