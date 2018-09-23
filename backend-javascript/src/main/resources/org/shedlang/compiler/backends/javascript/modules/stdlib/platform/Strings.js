"use strict";

function charToHexString(char) {
    return char.charCodeAt(0).toString(16).toUpperCase();
}

function charToString(char) {
    return char;
}

function codePointCount(string) {
    let count = 0;
    for (const char of string) {
        count++;
    }
    return count;
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

function substring(startIndex, endIndex, string) {
    return string.substring(startIndex, endIndex);
}

exports.charToHexString = charToHexString;
exports.charToString = charToString;
exports.codePointCount = codePointCount;
exports.mapCharacters = mapCharacters;
exports.repeat = repeat;
exports.replace = replace;
exports.substring = substring;
