package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.backends.wasm.runtime.callMalloc
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmConstValue
import org.shedlang.compiler.backends.wasm.wasm.WasmFuncType
import org.shedlang.compiler.backends.wasm.wasm.WasmInstruction
import org.shedlang.compiler.backends.wasm.wasm.WasmParam
import org.shedlang.compiler.stackir.LocalLoad

internal object WasmClosures {
    internal fun compileCreate(
        functionName: String,
        freeVariables: List<LocalLoad>,
        positionalParams: List<WasmParam>,
        namedParams: List<Pair<Identifier, WasmParam>>,
        compileBody: (WasmFunctionContext) -> WasmFunctionContext,
        context: WasmFunctionContext,
    ): Pair<WasmFunctionContext, WasmLocalRef> {
        val (context2, functionTableIndex) = compileFunction(
            functionName = functionName,
            freeVariables = freeVariables,
            positionalParams = positionalParams,
            namedParams = namedParams,
            compileBody = compileBody,
            context = context,
        )

        return compileCreateForFunction(
            tableIndex = functionTableIndex,
            freeVariables = freeVariables,
            context = context2,
        )
    }

    internal fun compileFunction(
        functionName: String,
        freeVariables: List<LocalLoad>,
        positionalParams: List<WasmParam>,
        namedParams: List<Pair<Identifier, WasmParam>>,
        compileBody: (WasmFunctionContext) -> WasmFunctionContext,
        context: WasmFunctionContext,
    ): Pair<WasmFunctionContext, WasmConstValue.TableEntryIndex> {
        val params = listOf(
            WasmParam(
                WasmNaming.environmentPointer,
                type = WasmData.closurePointerType
            )
        ) +
            positionalParams +
            namedParams.sortedBy { (paramName, _) -> paramName }
                .map { (_, param) -> param }

        val functionContext1 = WasmFunctionContext.initial()

        val functionContext2 = compileFreeVariablesLoad(
            freeVariables = freeVariables,
            context = functionContext1,
        )

        val functionContext3 = compileBody(functionContext2)
        val functionGlobalContext = functionContext3.toFunctionInGlobalContext(
            // TODO: uniquify name
            identifier = functionName,
            params = params,
            results = listOf(WasmData.genericValueType),
        )
        return Pair(
            context.mergeGlobalContext(functionGlobalContext),
            WasmConstValue.TableEntryIndex(functionName),
        )
    }

    internal fun compileCreateForFunction(
        tableIndex: WasmConstValue,
        freeVariables: List<LocalLoad>,
        context: WasmFunctionContext,
    ): Pair<WasmFunctionContext, WasmLocalRef> {
        val (context2, closure) = context.addLocal("closure")
        val context3 = context2.addInstruction(closure.set(
            callMalloc(
                size = WasmData.FUNCTION_POINTER_SIZE + WasmData.VALUE_SIZE * freeVariables.size,
                alignment = WasmData.closureAlignment,
            ),
        ))
        val context4 = compileFreeVariablesStore(
            closure = closure.get(),
            freeVariables = freeVariables,
            context = context3,
        )
        val context5 = context4.addInstruction(Wasm.I.i32Store(
            closure.get(),
            value = Wasm.I.i32Const(tableIndex),
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
            type = wasmFuncType,
            tableIndex = Wasm.I.i32Load(closurePointer),
            args = listOf(Wasm.I.i32Add(closurePointer, Wasm.I.i32Const(WasmData.FUNCTION_POINTER_SIZE))) + args,
        ))
    }

    private fun compileFreeVariablesStore(
        closure: WasmInstruction.Folded,
        freeVariables: List<LocalLoad>,
        context: WasmFunctionContext,
    ): WasmFunctionContext {
        return freeVariables.foldIndexed(context) { freeVariableIndex, currentContext, freeVariable ->
            currentContext.variableToStoredLocal(
                variableId = freeVariable.variableId,
                onStore = { local ->
                    Wasm.I.i32Store(
                        address = closure,
                        offset = WasmData.FUNCTION_POINTER_SIZE + WasmData.VALUE_SIZE * freeVariableIndex,
                        value = local.get(),
                        alignment = WasmData.closureAlignment,
                    )
                }
            )
        }
    }

    private fun compileFreeVariablesLoad(
        freeVariables: List<LocalLoad>,
        context: WasmFunctionContext
    ): WasmFunctionContext {
        return freeVariables.foldIndexed(context) { freeVariableIndex, currentContext, freeVariable ->
            val (currentContext2, local) = currentContext.variableToLocal(freeVariable.variableId, freeVariable.name)

            currentContext2.addInstruction(local.set(
                Wasm.I.i32Load(
                    address = Wasm.I.localGet(WasmNaming.environmentPointer),
                    offset = WasmData.VALUE_SIZE * freeVariableIndex,
                    alignment = WasmData.closureAlignment,
                ),
            ))
        }
    }
}
