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
        fields.$shedType = shape;
        return fields;
    }

    shape.typeName = name;

    return shape;
}

function isType(value, type) {
    return value != null && value.$shedType === type;
}

function symbolFactory() {
    const symbols = new Map();
    return function(name) {
        if (!symbols.has(name)) {
            symbols.set(name, {});
        }
        return symbols.get(name);
    };
}

module.exports = {
    declareShape: declareShape,
    isType: isType,
    symbolFactory: symbolFactory,

    intToString: intToString,
    list: list,
    print: print,
};
