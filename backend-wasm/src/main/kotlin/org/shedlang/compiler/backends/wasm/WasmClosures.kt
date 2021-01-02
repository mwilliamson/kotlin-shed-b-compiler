package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.backends.wasm.runtime.callMalloc
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmFuncType
import org.shedlang.compiler.backends.wasm.wasm.WasmInstruction
import org.shedlang.compiler.stackir.LocalLoad

internal object WasmClosures {
    internal fun compileCreate(
        functionIndex: WasmInstruction.Folded,
        freeVariables: List<LocalLoad>,
        context: WasmFunctionContext,
    ): Pair<WasmFunctionContext, String> {
        val (context2, closure) = context.addLocal("closure")

        val context3 = context2.addInstruction(Wasm.I.localSet(
            closure,
            callMalloc(
                size = Wasm.I.i32Const(WasmData.FUNCTION_POINTER_SIZE + WasmData.VALUE_SIZE * freeVariables.size),
                alignment = Wasm.I.i32Const(WasmData.closureAlignment),
            ),
        ))

        val context4 = compileFreeVariablesStore(
            closure = Wasm.I.localGet(closure),
            freeVariables = freeVariables,
            context = context3,
        )

        val context5 = context4.addInstruction(Wasm.I.i32Store(
            Wasm.I.localGet(closure),
            functionIndex,
        ))

        return Pair(context5, closure)
    }

    internal fun compileCall(
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

    private fun compileFreeVariablesStore(
        closure: WasmInstruction.Folded,
        freeVariables: List<LocalLoad>,
        context: WasmFunctionContext,
    ): WasmFunctionContext {
        return freeVariables.foldIndexed(context) { freeVariableIndex, currentContext, freeVariable ->
            val (currentContext2, local) = currentContext.variableToLocal(freeVariable.variableId, freeVariable.name)

            currentContext2.addInstruction(Wasm.I.i32Store(
                address = closure,
                offset = WasmData.FUNCTION_POINTER_SIZE + WasmData.VALUE_SIZE * freeVariableIndex,
                value = Wasm.I.localGet(local),
                alignment = WasmData.closureAlignment,
            ))
        }
    }

    internal fun compileFreeVariablesLoad(
        freeVariables: List<LocalLoad>,
        context: WasmFunctionContext
    ): WasmFunctionContext {
        return freeVariables.foldIndexed(context) { freeVariableIndex, currentContext, freeVariable ->
            val (currentContext2, local) = currentContext.variableToLocal(freeVariable.variableId, freeVariable.name)

            currentContext2.addInstruction(Wasm.I.localSet(
                local,
                Wasm.I.i32Load(
                    address = Wasm.I.localGet(WasmNaming.closurePointer),
                    offset = WasmData.FUNCTION_POINTER_SIZE + WasmData.VALUE_SIZE * freeVariableIndex,
                    alignment = WasmData.closureAlignment,
                ),
            ))
        }
    }
}
