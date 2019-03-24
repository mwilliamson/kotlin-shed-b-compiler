"use strict";
var Options = require("../Options");

function codePointToHexString(char) {
    return char.codePointAt(0).toString(16).toUpperCase();
}

function codePointToString(char) {
    return char;
}

function codePointCount(string) {
    let count = 0;
    for (const char of string) {
        count++;
    }
    return count;
}

function firstCodePoint(string) {
    if (string.length === 0) {
        return Options.none;
    } else {
        return Options.some(String.fromCodePoint(string.codePointAt(0)))
    }
}

function flatMapCodePoints(func, string) {
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

exports.codePointToHexString = codePointToHexString;
exports.codePointToString = codePointToString;
exports.codePointCount = codePointCount;
exports.firstCodePoint = firstCodePoint;
exports.flatMapCodePoints = flatMapCodePoints;
exports.repeat = repeat;
exports.replace = replace;
exports.substring = substring;
