package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.backends.ShedRuntime
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmConstValue
import org.shedlang.compiler.backends.wasm.wasm.WasmInstruction
import org.shedlang.compiler.backends.wasm.wasm.WasmParam

internal object WasmNativeModules {
    private val modules = mapOf<
        ModuleName,
        (WasmFunctionContext) -> Pair<WasmFunctionContext, List<Pair<Identifier, WasmInstruction.Folded>>>,
    >(
        listOf(Identifier("Core"), Identifier("Cast")) to ::generateCoreCastModule,
        listOf(Identifier("Core"), Identifier("Io")) to ::generateCoreIoModule,
        listOf(Identifier("Core"), Identifier("IntToString")) to ::generateCoreIntToStringModule,
        listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("Process")) to ::generateStdlibPlatformProcessModule,
    )

    fun moduleInitialisation(moduleName: ModuleName) = modules[moduleName]

    private fun generateCoreCastModule(
        context: WasmFunctionContext,
    ): Pair<WasmFunctionContext, List<Pair<Identifier, WasmInstruction.Folded>>> {
        val symbolName = ShedRuntime.functionSymbolName(
            listOf(Identifier("Core"), Identifier("Cast")),
            Identifier("cast"),
        )
        val intToStringImport = Wasm.importFunction(
            moduleName = "env",
            entityName = symbolName,
            identifier = symbolName,
            params = listOf(WasmData.genericValueType, WasmData.genericValueType, WasmData.genericValueType),
            results = listOf(WasmData.genericValueType),
        )
        val (context2, closure) = WasmClosures.compileCreateForFunction(
            tableIndex = WasmConstValue.TableEntryIndex(symbolName),
            freeVariables = listOf(),
            context.addImport(intToStringImport).addTableEntry(intToStringImport.identifier),
        )
        val exports = listOf(
            Pair(Identifier("cast"), Wasm.I.localGet(closure))
        )
        val context3 = context2.addDependency(listOf(Identifier("Core"), Identifier("Options")))
        return Pair(context3, exports)
    }

    private fun generateCoreIoModule(
        context: WasmFunctionContext,
    ): Pair<WasmFunctionContext, List<Pair<Identifier, WasmInstruction.Folded>>> {
        val symbolName = ShedRuntime.functionSymbolName(
            listOf(Identifier("Core"), Identifier("Io")),
            Identifier("print"),
        )
        val (context2, closure) = WasmClosures.compileCreate(
            functionName = symbolName,
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
        val symbolName = ShedRuntime.functionSymbolName(
            listOf(Identifier("Core"), Identifier("IntToString")),
            Identifier("intToString"),
        )
        val intToStringImport = Wasm.importFunction(
            moduleName = "env",
            entityName = symbolName,
            identifier = symbolName,
            params = listOf(WasmData.genericValueType, WasmData.genericValueType),
            results = listOf(WasmData.genericValueType),
        )
        val (context2, closure) = WasmClosures.compileCreateForFunction(
            tableIndex = WasmConstValue.TableEntryIndex(symbolName),
            freeVariables = listOf(),
            context.addImport(intToStringImport).addTableEntry(intToStringImport.identifier),
        )
        val exports = listOf(
            Pair(Identifier("intToString"), Wasm.I.localGet(closure))
        )
        return Pair(context2, exports)
    }

    private fun generateStdlibPlatformProcessModule(
        context: WasmFunctionContext,
    ): Pair<WasmFunctionContext, List<Pair<Identifier, WasmInstruction.Folded>>> {
        val symbolName = ShedRuntime.functionSymbolName(
            listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("Process")),
            Identifier("args"),
        )
        val argsImport = Wasm.importFunction(
            moduleName = "env",
            entityName = symbolName,
            identifier = symbolName,
            params = listOf(),
            results = listOf(WasmData.genericValueType),
        )
        val (context2, closure) = WasmClosures.compileCreateForFunction(
            tableIndex = WasmConstValue.TableEntryIndex(symbolName),
            freeVariables = listOf(),
            context.addImport(argsImport).addTableEntry(argsImport.identifier),
        )
        val exports = listOf(
            Pair(Identifier("args"), Wasm.I.localGet(closure))
        )
        return Pair(context2, exports)
    }
}
