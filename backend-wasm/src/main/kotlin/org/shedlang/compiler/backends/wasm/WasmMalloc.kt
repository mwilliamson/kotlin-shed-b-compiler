package org.shedlang.compiler.backends.wasm

internal fun generateMalloc(memory: WasmMemory): Pair<WasmMemory, SExpression> {
    val (memory2, heapStartPointer) = memory.staticAllocI32()
    val (memory3, heapEndPointer) = memory2.staticAllocI32(0)
    memory3.addStartInstructions(
        Wat.I.i32Const(heapStartPointer),
        Wat.I.memorySize,
        Wat.I.i32Store,

        Wat.I.i32Const(heapEndPointer),
        Wat.I.memorySize,
        Wat.I.i32Store,
    )

    val malloc = Wat.func(
        identifier = WasmCoreNames.malloc,
        params = listOf(Wat.param("size", Wat.i32), Wat.param("alignment", Wat.i32)),
        result = Wat.i32,
        body = listOf(
            Wat.I.localGet("size"),
            Wat.I.i32Const(WasmMemory.PAGE_SIZE - 1),
            Wat.I.i32Add,
            Wat.I.i32Const(WasmMemory.PAGE_SIZE),
            Wat.I.i32DivU,
            Wat.I.memoryGrow,
            Wat.I.i32Const(WasmMemory.PAGE_SIZE),
            Wat.I.i32Mul,
        ),
    )

    return Pair(memory3, malloc)
}
