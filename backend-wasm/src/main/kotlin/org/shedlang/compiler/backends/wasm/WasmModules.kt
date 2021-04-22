package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.backends.ShedRuntime
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmConstValue
import org.shedlang.compiler.backends.wasm.wasm.WasmInstruction
import org.shedlang.compiler.types.ModuleType

internal object WasmModules {
    internal fun compileStore(
        moduleName: ModuleName,
        moduleType: ModuleType,
        exports: List<Pair<Identifier, WasmInstruction.Folded>>,
        context: WasmFunctionContext,
    ): WasmFunctionContext {
        val layout = WasmObjects.layout(moduleType)
        val (context2, moduleValue) = context.addStaticData(
            size = layout.size,
            alignment = layout.alignment,
            name = ShedRuntime.moduleValueSymbolName(moduleName),
        )

        val context3 = WasmObjects.compileObjectStore(
            objectPointer = Wasm.I.i32Const(moduleValue),
            objectType = moduleType,
            fieldValues = exports,
            context = context2,
        )

        return context3
            .addInstruction(Wasm.I.globalSet(WasmNaming.moduleIsInited(moduleName), Wasm.I.i32Const(1)))
    }

    internal fun compileLoad(moduleName: ModuleName): WasmInstruction.Folded {
        val symbolName = ShedRuntime.moduleValueSymbolName(moduleName)
        return Wasm.I.i32Const(WasmConstValue.DataIndexByName(symbolName))
    }
}
