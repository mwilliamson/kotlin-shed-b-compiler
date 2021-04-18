package org.shedlang.compiler.backends.wasm.runtime

import org.shedlang.compiler.backends.wasm.WasmData.booleanType
import org.shedlang.compiler.backends.wasm.WasmGlobalContext
import org.shedlang.compiler.backends.wasm.WasmNaming
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmInstruction

internal fun generateStringEqualsFunc(): WasmGlobalContext {
    val function = Wasm.function(
        WasmNaming.Runtime.stringEquals,
        params = listOf(Wasm.param("left", stringPointerType), Wasm.param("right", stringPointerType)),
        locals = listOf(Wasm.local("index", stringIndexType), Wasm.local("length", stringLengthType)),
        results = listOf(booleanType),
        body = listOf(
            Wasm.I.if_(
                results = listOf(booleanType),
                condition = Wasm.I.i32NotEqual(
                    loadStringLength(Wasm.I.localGet("left")),
                    loadStringLength(Wasm.I.localGet("right")),
                ),
                ifTrue = listOf(Wasm.I.i32Const(0)),
                ifFalse = listOf(
                    Wasm.I.localSet(
                        "length",
                        loadStringLength(Wasm.I.localGet("left"))
                    ),
                    Wasm.I.localSet(
                        "index",
                        Wasm.I.i32Const(0),
                    ),

                    Wasm.I.loop(identifier="iterate_chars", results = listOf(booleanType)), // iterate_chars
                    Wasm.I.i32GreaterThanOrEqualUnsigned(
                        Wasm.I.localGet("index"),
                        Wasm.I.localGet("length"),
                    ),

                    Wasm.I.if_(results = listOf(booleanType)), // string_end

                    Wasm.I.i32Const(1),

                    Wasm.I.else_, // string_end

                    // Get left char
                    Wasm.I.localGet("left"),
                    Wasm.I.i32Const(4),
                    Wasm.I.i32Add,
                    Wasm.I.localGet("index"),
                    Wasm.I.i32Add,
                    Wasm.I.i32Load8Unsigned,

                    // Get right char
                    Wasm.I.localGet("right"),
                    Wasm.I.i32Const(4),
                    Wasm.I.i32Add,
                    Wasm.I.localGet("index"),
                    Wasm.I.i32Add,
                    Wasm.I.i32Load8Unsigned,

                    // Compare chars
                    Wasm.I.i32Equals,
                    Wasm.I.if_(results = listOf(booleanType)),
                    Wasm.I.localGet("index"),
                    Wasm.I.i32Const(1),
                    Wasm.I.i32Add,
                    Wasm.I.localSet("index"),
                    Wasm.I.branch("iterate_chars"), // iterate_chars
                    Wasm.I.else_,
                    Wasm.I.i32Const(0),
                    Wasm.I.end,

                    Wasm.I.end, // string_end
                    Wasm.I.end, // iterate_chars
                ),
            ),
        ),
    )
    return WasmGlobalContext.initial().addStaticFunction(function)
}

private fun loadStringLength(string: WasmInstruction.Folded): WasmInstruction.Folded {
    return Wasm.I.i32Load(string)
}

private val stringLengthType = Wasm.T.i32
private val stringIndexType = stringLengthType
private val stringPointerType = Wasm.T.i32
