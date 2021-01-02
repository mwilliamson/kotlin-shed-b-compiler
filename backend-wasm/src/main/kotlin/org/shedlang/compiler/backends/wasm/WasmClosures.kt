package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmFuncType
import org.shedlang.compiler.backends.wasm.wasm.WasmInstruction

internal fun compileClosureCall(
    closurePointer: WasmInstruction.Folded,
    positionalArguments: List<WasmInstruction.Folded>,
    namedArguments: List<Pair<Identifier, WasmInstruction.Folded>>,
    context: WasmFunctionContext,
): WasmFunctionContext {
    val argumentCount = positionalArguments.size + namedArguments.size
    val wasmFuncType = WasmFuncType(
        params = listOf(WasmData.functionPointerType) + (0 until argumentCount).map { WasmData.genericValueType },
        results = listOf(WasmData.genericValueType),
    )

    val sortedNamedArgLocals = namedArguments
        .sortedBy { (argName, _) -> argName }
        .map { (_, argValue) -> argValue }

    val args = positionalArguments + sortedNamedArgLocals

    return context.addInstruction(Wasm.I.callIndirect(
        type = wasmFuncType.identifier(),
        tableIndex = Wasm.I.i32Load(closurePointer),
        args = listOf(closurePointer) + args,
    ))
}
