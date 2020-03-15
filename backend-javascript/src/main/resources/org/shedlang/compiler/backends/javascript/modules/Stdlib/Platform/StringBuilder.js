"use strict";

const stringBuilders = [];

function build(func) {
    stringBuilders.push([]);
    func();
    const stringBuilder = stringBuilders.pop();
    return stringBuilder.join("");
}

function write(value) {
    stringBuilders[stringBuilders.length - 1].push(value);
}

exports.build = build;
exports.write = write;
