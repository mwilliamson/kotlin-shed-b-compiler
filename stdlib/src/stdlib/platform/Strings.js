function charToHexString(char) {
    return char.charCodeAt(0).toString(16).toUpperCase();
}

function charToString(char) {
    return char;
}

function length(string) {
    return string.length;
}

function mapCharacters(func, string) {
    let result = "";
    for (const char of string) {
        result += func(char);
    }
    return result;
}

function repeat(string, times) {
    let result = "";
    for (let i = 0; i < times; i++) {
      result += string;
    }
    return result;
}

function replace(old, replacement, string) {
    return string.split(old).join(replacement);
}

exports.charToHexString = charToHexString;
exports.charToString = charToString;
exports.length = length;
exports.mapCharacters = mapCharacters;
exports.repeat = repeat;
exports.replace = replace;
