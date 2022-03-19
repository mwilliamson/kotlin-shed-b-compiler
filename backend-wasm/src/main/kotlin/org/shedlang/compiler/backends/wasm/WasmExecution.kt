package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.findRoot
import java.nio.file.Path

internal fun generateWasmCommand(path: Path, args: List<String>): List<String> {
    return listOf(
        "node",
        "--experimental-wasi-unstable-preview1",
        "--experimental-wasm-eh",
        "--experimental-wasm-return_call",
        "--no-warnings",
        findRoot().resolve("backend-wasm/run-wasm.js").toAbsolutePath().toString(),
        path.toAbsolutePath().toString(),
    ) + args
}

