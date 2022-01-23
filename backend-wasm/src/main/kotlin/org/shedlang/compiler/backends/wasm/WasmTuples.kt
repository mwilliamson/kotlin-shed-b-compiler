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
        val context4 = context3.addInstruction(tuplePointer.set(
            callMalloc(
                size = WasmData.VALUE_SIZE * length,
                alignment = WasmData.VALUE_SIZE,
            ),
        ))

        val context5 = (length - 1 downTo 0).fold(context4) { currentContext, elementIndex ->
            currentContext
                .addInstruction(element.set())
                .addInstruction(Wasm.I.i32Store(
                    offset = elementIndex * WasmData.VALUE_SIZE,
                    alignment = WasmData.VALUE_SIZE,
                    address = tuplePointer.get(),
                    value = element.get(),
                ))
        }

        return context5.addInstruction(tuplePointer.get())
    }
}
