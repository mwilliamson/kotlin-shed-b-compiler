var Options = require("../Options");


function name(type) {
    return type.typeName;
}

function typeOf(value) {
    return value.$shedType;
}

module.exports = {
    name: name,
    typeOf: typeOf,
};
