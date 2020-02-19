"use strict";
var Options = require("../../Core/Options");

function unicodeScalarToHexString(char) {
    return char.codePointAt(0).toString(16).toUpperCase();
}

function unicodeScalarToInt(char) {
    return BigInt(char.codePointAt(0));
}

function unicodeScalarToString(char) {
    return char;
}

function unicodeScalarCount(string) {
    let count = 0;
    for (const char of string) {
        count++;
    }
    return BigInt(count);
}

function dropLeftUnicodeScalars(count, string) {
    const index = indexAtUnicodeScalarCount(Number(count), string);
    return string.substring(index);
}

function indexAtUnicodeScalarCount(endCount, value) {
    if (endCount >= 0) {
        let index = 0;
        let count = 0;
        while (count < endCount && index < value.length) {
            const unicodeScalar = value.codePointAt(index);
            const size = unicodeScalar > 0xffff ? 2 : 1;
            count += 1;
            index += size;
        }
        return index;
    } else {
        let index = value.length;
        let count = 0;
        while (count > endCount && index - 1 >= 0) {
            const codeUnit = value.charCodeAt(index - 1);
            const size = codeUnit >= 0xdc00 && codeUnit <= 0xdfff ? 2 : 1;
            count -= 1;
            index -= size;
        }
        return index;
    }
}

function next(stringSlice) {
    const {string, startIndex, endIndex} = stringSlice;
    if (startIndex < endIndex) {
        const unicodeScalar = string.codePointAt(startIndex);
        const size = unicodeScalar > 0xffff ? 2 : 1;
        const rest = {string: string, startIndex: startIndex + size, endIndex: endIndex}
        return Options.some([String.fromCodePoint(unicodeScalar), rest]);
    } else {
        return Options.none;
    }
}

function replace(old, replacement, string) {
    return string.split(old).join(replacement);
}

function slice(string) {
    return {string: string, startIndex: 0, endIndex: string.length};
}

function substring(startCount, endCount, string) {
    const startIndex = indexAtUnicodeScalarCount(Number(startCount), string);
    const endIndex = indexAtUnicodeScalarCount(Number(endCount), string);
    if (startIndex < endIndex) {
        return string.substring(startIndex, endIndex);
    } else {
        return "";
    }
}

exports.unicodeScalarToHexString = unicodeScalarToHexString;
exports.unicodeScalarToInt = unicodeScalarToInt;
exports.unicodeScalarToString = unicodeScalarToString;
exports.unicodeScalarCount = unicodeScalarCount;
exports.dropLeftUnicodeScalars = dropLeftUnicodeScalars;
exports.next = next;
exports.replace = replace;
exports.slice = slice;
exports.substring = substring;
