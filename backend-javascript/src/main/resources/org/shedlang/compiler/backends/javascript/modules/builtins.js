function intToString(value) {
    return value.toString();
}

function print(value) {
    process.stdout.write(value);
}

function list() {
    return Array.prototype.slice.call(arguments);
}

function declareShape(name, constantFields) {
    function shape(fields) {
        if (fields === undefined) {
            fields = {};
        }
        for (var key in constantFields) {
            fields[key] = constantFields[key];
        }
        return fields;
    }

    shape.typeName = name;

    return shape;
}

module.exports = {
    declareShape: declareShape,

    intToString: intToString,
    list: list,
    print: print,
};
