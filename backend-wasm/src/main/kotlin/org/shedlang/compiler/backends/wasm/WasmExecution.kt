package org.shedlang.compiler.backends.wasm

import java.nio.file.Path

internal fun generateWasmCommand(path: Path, args: List<String>): List<String> {
    return listOf("wasmtime", path.toAbsolutePath().toString()) + args
}

