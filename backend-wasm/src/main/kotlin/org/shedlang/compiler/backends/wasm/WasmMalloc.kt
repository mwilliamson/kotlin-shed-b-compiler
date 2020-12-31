package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.backends.wasm.wasm.*
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmFunction
import org.shedlang.compiler.backends.wasm.wasm.WasmInstruction

internal fun generateMalloc(memory: WasmMemory): Pair<WasmMemory, WasmFunction> {
    val (memory2, heapPointer) = memory.staticAllocI32()
    val (memory3, heapEndPointer) = memory2.staticAllocI32(0)
    memory3.addStartInstructions(
        Wasm.I.i32Store(
            Wasm.I.i32Const(heapPointer),
            Wasm.I.i32Multiply(Wasm.I.memorySize, Wasm.I.i32Const(WasmMemory.PAGE_SIZE)),
        ),

        Wasm.I.i32Store(
            Wasm.I.i32Const(heapEndPointer),
            Wasm.I.i32Multiply(Wasm.I.memorySize, Wasm.I.i32Const(WasmMemory.PAGE_SIZE)),
        ),
    )

    // TODO: test this!
    val malloc = Wasm.function(
        identifier = WasmCoreNames.malloc,
        params = listOf(Wasm.param("size", Wasm.T.i32), Wasm.param("alignment", Wasm.T.i32)),
        locals = listOf(Wasm.local("grow", Wasm.T.i32), Wasm.local("result", Wasm.T.i32), Wasm.local("heap_pointer", Wasm.T.i32)),
        results = listOf(Wasm.T.i32),
        body = listOf(
            // Get heap pointer
            Wasm.I.localSet("heap_pointer", Wasm.I.i32Load(heapPointer)),

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
            Wasm.I.i32Store(
                Wasm.I.i32Const(heapPointer),
                Wasm.I.i32Add(
                    Wasm.I.localGet("heap_pointer"),
                    Wasm.I.localGet("size"),
                ),
            ),

            // Grow heap if necessary
            // TODO: grow by more than necessary?
            Wasm.I.localGet("heap_pointer"),
            Wasm.I.i32Const(heapEndPointer),
            Wasm.I.i32Load,
            Wasm.I.i32Sub,
            Wasm.I.localSet("grow"),
            Wasm.I.localGet("grow"),
            Wasm.I.i32Const(0),
            Wasm.I.i32LessThanOrEqualUnsigned,
            Wasm.I.if_(results = listOf()),
            Wasm.I.else_,

            // TODO: check for error from memory.grow
            Wasm.I.drop(Wasm.I.memoryGrow(
                Wasm.I.i32DivideUnsigned(
                    Wasm.I.i32Add(Wasm.I.localGet("grow"), Wasm.I.i32Const(WasmMemory.PAGE_SIZE - 1)),
                    Wasm.I.i32Const(WasmMemory.PAGE_SIZE),
                ),
            )),

            Wasm.I.i32Store(
                Wasm.I.i32Const(heapEndPointer),
                Wasm.I.i32Multiply(
                    Wasm.I.memorySize,
                    Wasm.I.i32Const(WasmMemory.PAGE_SIZE),
                ),
            ),

            Wasm.I.end,

            Wasm.I.localGet("result"),
        ),
    )

    return Pair(memory3, malloc)
}

internal fun callMalloc(size: WasmInstruction.Folded, alignment: WasmInstruction.Folded): WasmInstruction.Folded {
    return Wasm.I.call(
        WasmCoreNames.malloc,
        listOf(size, alignment),
    )
}
