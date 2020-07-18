"use strict";

const stringBuilders = [];

function build(func) {
    stringBuilders.push([]);
    func();
    const stringBuilder = stringBuilders.pop();
    return stringBuilder.join("");
}

async function buildAsync(func) {
    stringBuilders.push([]);
    await func();
    const stringBuilder = stringBuilders.pop();
    return stringBuilder.join("");
}

build.async = buildAsync;

function write(value) {
    stringBuilders[stringBuilders.length - 1].push(value);
}

exports.build = build;
exports.write = write;
