"use strict";
var Options = require("../Options");

function codePointAt(index, string) {
    index = Number(index);
    if (index < string.length) {
        return Options.some(String.fromCodePoint(string.codePointAt(index)))
    } else {
        return Options.none;
    }
}

function codePointToHexString(char) {
    return char.codePointAt(0).toString(16).toUpperCase();
}

function codePointToInt(char) {
    return BigInt(char.codePointAt(0));
}

function codePointToString(char) {
    return char;
}

function codePointCount(string) {
    let count = 0;
    for (const char of string) {
        count++;
    }
    return BigInt(count);
}

function repeat(string, times) {
    let result = "";
    for (let i = 0n; i < times; i++) {
      result += string;
    }
    return result;
}

function replace(old, replacement, string) {
    return string.split(old).join(replacement);
}

function substring(startIndex, endIndex, string) {
    return string.substring(Number(startIndex), Number(endIndex));
}

exports.codePointAt = codePointAt;
exports.codePointToHexString = codePointToHexString;
exports.codePointToInt = codePointToInt;
exports.codePointToString = codePointToString;
exports.codePointCount = codePointCount;
exports.repeat = repeat;
exports.replace = replace;
exports.substring = substring;
