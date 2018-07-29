var Options = require("../Options");


function cast(type, value) {
    // TODO: handle subtyping
    if (value.$shedType === type) {
        return Options.some(value);
    } else {
        return Options.none;
    }
}

function name(type) {
    return type.typeName;
}

function typeOf(value) {
    return value.$shedType;
}

module.exports = {
    cast: cast,
    name: name,
    typeOf: typeOf,
};
