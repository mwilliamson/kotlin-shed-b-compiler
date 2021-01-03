package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmInstruction
import org.shedlang.compiler.backends.wasm.wasm.WasmParam

internal object WasmNativeModules {
    private val modules = mapOf<
        ModuleName,
        (WasmFunctionContext) -> Pair<WasmFunctionContext, List<Pair<Identifier, WasmInstruction.Folded>>>,
    >(
        listOf(Identifier("Core"), Identifier("Io")) to ::generateCoreIoModule
    )

    fun moduleInitialisation(moduleName: ModuleName) = modules[moduleName]

    private fun generateCoreIoModule(
        context: WasmFunctionContext,
    ): Pair<WasmFunctionContext, List<Pair<Identifier, WasmInstruction.Folded>>> {
        val (context2, closure) = WasmClosures.compileCreate(
            // TODO: build identifiers in WasmNaming
            functionName = "shed_module__core_io__print",
            freeVariables = listOf(),
            positionalParams = listOf(WasmParam("value", type = WasmData.genericValueType)),
            namedParams = listOf(),
            compileBody = { currentContext -> currentContext
                .addInstruction(Wasm.I.call(
                    identifier = WasmNaming.Runtime.print,
                    args = listOf(Wasm.I.localGet("value")),
                ))
                .addInstruction(WasmData.unitValue)
            },
            context,
        )
        val exports = listOf(
            Pair(Identifier("print"), Wasm.I.localGet(closure))
        )
        return Pair(context2, exports)
    }
}
