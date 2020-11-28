const Options = require("./Options");

function cast(type, value) {
    if (type.$typeTagValue === value.$tagValue) {
        return Options.some(value);
    } else {
        return Options.none;
    }
}

cast.async = cast;

module.exports = {
    cast: cast,
};
