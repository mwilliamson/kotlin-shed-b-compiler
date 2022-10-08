package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.InternalCompilerError
import org.shedlang.compiler.ast.NullSource

internal fun <K, V> MutableMap<K,V>.add(key: K, value: V) {
    if (this.putIfAbsent(key, value) !== null) {
        throw InternalCompilerError("duplicate key: $key", NullSource)
    }
}
