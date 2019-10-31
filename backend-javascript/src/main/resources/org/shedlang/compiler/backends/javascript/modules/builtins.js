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

function varargs(cons, nil) {
    return (...args) => {
        let result = nil;
        for (let index = args.length; index --> 0;) {
            result = cons(args[index], result);
        }
        return result;
    };
}

module.exports = {
    declareShape: declareShape,

    intToString: intToString,
    list: list,
    print: print,
    varargs: varargs,
};
