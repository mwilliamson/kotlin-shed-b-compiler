package org.shedlang.compiler.backends.wasm.runtime

import org.shedlang.compiler.backends.wasm.WASM_PAGE_SIZE
import org.shedlang.compiler.backends.wasm.WasmGlobalContext
import org.shedlang.compiler.backends.wasm.WasmNaming
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmInstruction

internal fun generateMalloc(): WasmGlobalContext {
    val globalContext = WasmGlobalContext.initial()
        .addMutableGlobal(
            identifier = WasmNaming.Runtime.heapPointer,
            type = Wasm.T.i32,
            initial = Wasm.I.i32Multiply(Wasm.I.memorySize, Wasm.I.i32Const(WASM_PAGE_SIZE)),
        )
        .addMutableGlobal(
            identifier = WasmNaming.Runtime.heapEndPointer,
            type = Wasm.T.i32,
            initial = Wasm.I.i32Multiply(Wasm.I.memorySize, Wasm.I.i32Const(WASM_PAGE_SIZE)),
        )

    // TODO: test this!
    val malloc = Wasm.function(
        identifier = WasmNaming.Runtime.malloc,
        params = listOf(Wasm.param("size", Wasm.T.i32), Wasm.param("alignment", Wasm.T.i32)),
        locals = listOf(Wasm.local("grow", Wasm.T.i32), Wasm.local("result", Wasm.T.i32), Wasm.local("heap_pointer", Wasm.T.i32)),
        results = listOf(Wasm.T.i32),
        body = listOf(
            // Get heap pointer
            Wasm.I.localSet("heap_pointer", Wasm.I.globalGet(WasmNaming.Runtime.heapPointer)),

            // Align heap pointer (heap_pointer + alignment - 1) & -alignment
            Wasm.I.localSet(
                "heap_pointer",
                Wasm.I.i32And(
                    Wasm.I.i32Add(
                        Wasm.I.i32Add(
                            Wasm.I.localGet("heap_pointer"),
                            Wasm.I.localGet("alignment"),
                        ),
                        Wasm.I.i32Const(-1),
                    ),
                    Wasm.I.i32Sub(Wasm.I.i32Const(0), Wasm.I.localGet("alignment")),
                ),
            ),

            // Set result
            Wasm.I.localSet("result", Wasm.I.localGet("heap_pointer")),

            // Update heap pointer
            Wasm.I.localSet(
                "heap_pointer",
                Wasm.I.i32Add(
                    Wasm.I.localGet("heap_pointer"),
                    Wasm.I.localGet("size"),
                )
            ),
            Wasm.I.globalSet(WasmNaming.Runtime.heapPointer, Wasm.I.localGet("heap_pointer")),

            // Grow heap if necessary
            // TODO: grow by more than necessary?
            Wasm.I.localSet(
                "grow",
                Wasm.I.i32Sub(
                    Wasm.I.localGet("heap_pointer"),
                    Wasm.I.globalGet(WasmNaming.Runtime.heapEndPointer),
                ),
            ),

            Wasm.I.if_(
                condition = Wasm.I.i32LessThanOrEqualUnsigned(
                    Wasm.I.localGet("grow"),
                    Wasm.I.i32Const(0),
                ),
                ifTrue = listOf(),
                ifFalse = listOf(
                    // TODO: check for error from memory.grow
                    Wasm.I.drop(Wasm.I.memoryGrow(
                        Wasm.I.i32DivideUnsigned(
                            Wasm.I.i32Add(Wasm.I.localGet("grow"), Wasm.I.i32Const(WASM_PAGE_SIZE - 1)),
                            Wasm.I.i32Const(WASM_PAGE_SIZE),
                        ),
                    )),

                    Wasm.I.globalSet(
                        WasmNaming.Runtime.heapEndPointer,
                        Wasm.I.i32Multiply(
                            Wasm.I.memorySize,
                            Wasm.I.i32Const(WASM_PAGE_SIZE),
                        ),
                    ),
                ),
            ),

            Wasm.I.localGet("result"),
        ),
    )

    return globalContext.addStaticFunction(malloc)
}

internal fun callMalloc(size: WasmInstruction.Folded, alignment: WasmInstruction.Folded): WasmInstruction.Folded {
    return Wasm.I.call(
        WasmNaming.Runtime.malloc,
        listOf(size, alignment),
    )
}
