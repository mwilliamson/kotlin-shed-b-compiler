"use strict";

const fs = require("fs");
const {WASI} = require("wasi");

const wasi = new WASI({
    args: process.argv,
    env: process.env,
    //~ preopens: {}
});
const importObject = {"wasi_snapshot_preview1": wasi.wasiImport};

(async () => {
    const wasm = await WebAssembly.compile(fs.readFileSync(process.argv[2]));
    const instance = await WebAssembly.instantiate(wasm, importObject);

    process.stdout.write(instance.exports.test().toString());
})();
