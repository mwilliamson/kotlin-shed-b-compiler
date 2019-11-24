"use strict";
var Options = require("../../Core/Options");

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

function next(index, string) {
    if (index < string.length) {
        const codePoint = string.codePointAt(index);
        const size = codePoint > 0xffff ? 2 : 1;
        return Options.some([String.fromCodePoint(codePoint), index + size]);
    } else {
        return Options.none;
    }
}

function replace(old, replacement, string) {
    return string.split(old).join(replacement);
}

function substring(startIndex, endIndex, string) {
    return string.substring(Number(startIndex), Number(endIndex));
}

const zeroIndex = 0;

exports.codePointToHexString = codePointToHexString;
exports.codePointToInt = codePointToInt;
exports.codePointToString = codePointToString;
exports.codePointCount = codePointCount;
exports.next = next;
exports.replace = replace;
exports.substring = substring;
exports.zeroIndex = zeroIndex;
