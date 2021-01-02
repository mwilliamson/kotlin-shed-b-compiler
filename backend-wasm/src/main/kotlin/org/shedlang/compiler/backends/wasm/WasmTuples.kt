package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.backends.wasm.runtime.callMalloc
import org.shedlang.compiler.backends.wasm.wasm.Wasm

object WasmTuples {
    internal fun compileAccess(elementIndex: Int, context: WasmFunctionContext): WasmFunctionContext {
        return context.addInstruction(Wasm.I.i32Load(
            offset = elementIndex * WasmData.VALUE_SIZE,
            alignment = WasmData.VALUE_SIZE,
        ))
    }

    internal fun compileCreate(
        length: Int,
        context: WasmFunctionContext,
    ): WasmFunctionContext {
        val (context2, tuplePointer) = context.addLocal("tuple")
        val (context3, element) = context2.addLocal("element")
        val context4 = context3.addInstruction(Wasm.I.localSet(
            tuplePointer,
            callMalloc(
                size = Wasm.I.i32Const(WasmData.VALUE_SIZE * length),
                alignment = Wasm.I.i32Const(WasmData.VALUE_SIZE),
            ),
        ))

        val context5 = (length - 1 downTo 0).fold(context4) { currentContext, elementIndex ->
            currentContext
                .addInstruction(Wasm.I.localSet(element))
                .addInstruction(Wasm.I.i32Store(
                    offset = elementIndex * WasmData.VALUE_SIZE,
                    alignment = WasmData.VALUE_SIZE,
                    address = Wasm.I.localGet(tuplePointer),
                    value = Wasm.I.localGet(element),
                ))
        }

        return context5.addInstruction(Wasm.I.localGet(tuplePointer))
    }
}
