package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmConstValue
import org.shedlang.compiler.backends.wasm.wasm.WasmInstruction
import org.shedlang.compiler.backends.wasm.wasm.WasmParam

internal object WasmNativeModules {
    private val modules = mapOf<
        ModuleName,
        (WasmFunctionContext) -> Pair<WasmFunctionContext, List<Pair<Identifier, WasmInstruction.Folded>>>,
    >(
        listOf(Identifier("Core"), Identifier("Io")) to ::generateCoreIoModule,
        listOf(Identifier("Core"), Identifier("IntToString")) to ::generateCoreIntToStringModule,
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

    private fun generateCoreIntToStringModule(
        context: WasmFunctionContext,
    ): Pair<WasmFunctionContext, List<Pair<Identifier, WasmInstruction.Folded>>> {
        val intToStringImport = Wasm.importFunction(
            moduleName = "env",
            entityName = "Shed_Core_IntToString__intToString",
            identifier = "Shed_Core_IntToString__intToString",
            params = listOf(WasmData.genericValueType, WasmData.genericValueType),
            results = listOf(WasmData.genericValueType),
        )
        val (context2, closure) = WasmClosures.compileCreateForFunction(
            // TODO: build identifiers in WasmNaming
            tableIndex = WasmConstValue.TableEntryIndex("Shed_Core_IntToString__intToString"),
            freeVariables = listOf(),
            context.addImport(intToStringImport).addTableEntry(intToStringImport.identifier),
        )
        val exports = listOf(
            Pair(Identifier("intToString"), Wasm.I.localGet(closure))
        )
        return Pair(context2, exports)
    }
}
