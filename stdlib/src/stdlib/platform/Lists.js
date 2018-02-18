var Builtins = require("../../builtins");
var Options = require("../options");
var Sequences = require("../Sequences");

function listToSequence(elements) {
    return listIndexToSequence(elements, 0);
}

function listIndexToSequence(elements, index) {
    return Sequences.Sequence({
        next: function() {
            if (index < elements.length) {
                return Options.some(Sequences.SequenceItem({
                    head: elements[index],
                    tail: listIndexToSequence(elements, index + 1)
                }));
            } else {
                return Options.none;
            }
        }
    });
}

function sequenceToList(sequence) {
    var result = [];
    
    while (true) {
        var item = sequence.next();
        if (Builtins.isType(item, Options.None)) {
            return result;
        } else {
            result.push(item.value.head);
            sequence = item.value.tail;
        }
    }
}

exports.listToSequence = listToSequence;
exports.sequenceToList = sequenceToList;
