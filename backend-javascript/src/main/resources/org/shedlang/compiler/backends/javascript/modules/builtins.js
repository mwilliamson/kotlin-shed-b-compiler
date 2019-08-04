function intToString(value) {
    return value.toString();
}

function print(value) {
    process.stdout.write(value);
}

function list() {
    return Array.prototype.slice.call(arguments);
}

function declareShape(name, fields) {
    function shape(fieldValues = {}) {
        return {...fieldValues, ...constantFieldValues};
    }

    shape.fields = {};

    const constantFieldValues = {};
    fields.forEach(field => {
        if (field.isConstant) {
            constantFieldValues[field.jsName] = field.value;
        }

        shape.fields[field.jsName] = field;
    })

    return shape;
}

module.exports = {
    declareShape: declareShape,

    intToString: intToString,
    list: list,
    print: print,
};
