function charToString(char) {
    return char;
}

function mapCharacters(func, string) {
    let result = "";
    for (const char of string) {
        result += func(char);
    }
    return result;
}

function replace(old, replacement, string) {
    return string.split(old).join(replacement);
}

exports.charToString = charToString;
exports.mapCharacters = mapCharacters;
exports.replace = replace;
