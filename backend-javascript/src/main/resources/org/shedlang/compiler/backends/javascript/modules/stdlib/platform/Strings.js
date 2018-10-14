"use strict";
var Options = require("../Options");

function charToHexString(char) {
    return char.codePointAt(0).toString(16).toUpperCase();
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

function firstChar(string) {
    if (string.length === 0) {
        return Options.none;
    } else {
        return Options.some(String.fromCodePoint(string.codePointAt(0)))
    }
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
exports.firstChar = firstChar;
exports.mapCharacters = mapCharacters;
exports.repeat = repeat;
exports.replace = replace;
exports.substring = substring;
