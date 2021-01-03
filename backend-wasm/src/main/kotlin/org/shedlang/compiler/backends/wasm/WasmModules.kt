package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmInstruction
import org.shedlang.compiler.types.ModuleType

internal object WasmModules {
    internal fun compileStore(
        moduleName: ModuleName,
        moduleType: ModuleType,
        exports: List<Pair<Identifier, WasmInstruction.Folded>>,
        context: WasmFunctionContext,
    ): WasmFunctionContext {
        val (context2, moduleValue) = context.addStaticData(
            size = exports.size * WasmData.VALUE_SIZE,
            alignment = WasmData.VALUE_SIZE,
        )

        val context3 = WasmObjects.compileObjectStore(
            objectPointer = Wasm.I.i32Const(moduleValue),
            objectType = moduleType,
            fieldValues = exports,
            context = context2,
        )

        return context3
            .addInstruction(Wasm.I.globalSet(WasmNaming.moduleIsInited(moduleName), Wasm.I.i32Const(1)))
            .addImmutableGlobal(
                identifier = WasmNaming.moduleValue(moduleName),
                type = WasmData.moduleValuePointerType,
                value = Wasm.I.i32Const(moduleValue),
            )
    }

    internal fun compileLoad(moduleName: ModuleName) =
        Wasm.I.globalGet(WasmNaming.moduleValue(moduleName))
}
