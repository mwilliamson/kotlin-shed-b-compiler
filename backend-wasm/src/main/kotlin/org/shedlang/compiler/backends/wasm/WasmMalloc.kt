package org.shedlang.compiler.backends.wasm

internal fun generateMalloc(memory: WasmMemory): Pair<WasmMemory, SExpression> {
    val (memory2, heapPointer) = memory.staticAllocI32()
    val (memory3, heapEndPointer) = memory2.staticAllocI32(0)
    memory3.addStartInstructions(
        Wat.I.i32Const(heapPointer),
        Wat.I.memorySize,
        Wat.I.i32Const(WasmMemory.PAGE_SIZE),
        Wat.I.i32Mul,
        Wat.I.i32Store,

        Wat.I.i32Const(heapEndPointer),
        Wat.I.memorySize,
        Wat.I.i32Const(WasmMemory.PAGE_SIZE),
        Wat.I.i32Mul,
        Wat.I.i32Store,
    )

    // TODO: test this!
    val malloc = Wat.func(
        identifier = WasmCoreNames.malloc,
        params = listOf(Wat.param("size", Wat.i32), Wat.param("alignment", Wat.i32)),
        locals = listOf(Wat.local("grow", Wat.i32), Wat.local("result", Wat.i32), Wat.local("heap_pointer", Wat.i32)),
        result = Wat.i32,
        body = listOf(
            // Get heap pointer
            Wat.I.i32Const(heapPointer),
            Wat.I.i32Load,
            Wat.I.localSet("heap_pointer"),

            // Align heap pointer (heap_pointer + alignment - 1) & -alignment
            Wat.I.localGet("heap_pointer"),
            Wat.I.localGet("alignment"),
            Wat.I.i32Add,
            Wat.I.i32Const(-1),
            Wat.I.i32Add,
            Wat.I.i32Const(0),
            Wat.I.localGet("alignment"),
            Wat.I.i32Sub,
            Wat.I.i32And,
            Wat.I.localSet("heap_pointer"),

            // Set result
            Wat.I.localGet("heap_pointer"),
            Wat.I.localSet("result"),

            // Update heap pointer
            Wat.I.i32Const(heapPointer),
            Wat.I.localGet("heap_pointer"),
            Wat.I.localGet("size"),
            Wat.I.i32Add,
            Wat.I.i32Store,

            // Grow heap if necessary
            // TODO: grow by more than necessary?
            Wat.I.localGet("heap_pointer"),
            Wat.I.i32Const(heapEndPointer),
            Wat.I.i32Load,
            Wat.I.i32Sub,
            Wat.I.localSet("grow"),
            Wat.I.localGet("grow"),
            Wat.I.i32Const(0),
            Wat.I.i32LeU,
            *Wat.I.if_(result = listOf()).toTypedArray(),
            Wat.I.else_,

            Wat.I.localGet("grow"),
            Wat.I.i32Const(WasmMemory.PAGE_SIZE - 1),
            Wat.I.i32Add,
            Wat.I.i32Const(WasmMemory.PAGE_SIZE),
            Wat.I.i32DivU,
            Wat.I.memoryGrow,
            // TODO: check for error
            Wat.I.drop,

            Wat.I.i32Const(heapEndPointer),
            Wat.I.memorySize,
            Wat.I.i32Const(WasmMemory.PAGE_SIZE),
            Wat.I.i32Mul,
            Wat.I.i32Store,

            Wat.I.end,

            Wat.I.localGet("result"),
        ),
    )

    return Pair(memory3, malloc)
}
