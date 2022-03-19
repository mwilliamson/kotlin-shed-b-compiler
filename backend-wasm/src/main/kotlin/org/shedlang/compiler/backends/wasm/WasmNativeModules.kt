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
        listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("StringBuilder")) to ::generateStdlibPlatformStringBuilderModule,
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
            Pair(Identifier("cast"), closure.get())
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
            Pair(Identifier("print"), closure.get())
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
            Pair(Identifier("intToString"), closure.get())
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
            Pair(Identifier("args"), closure.get())
        )
        return Pair(context2, exports)
    }

    private fun generateStdlibPlatformStringBuilderModule(
        context: WasmFunctionContext,
    ): Pair<WasmFunctionContext, List<Pair<Identifier, WasmInstruction.Folded>>> {

        val buildSymbolName = ShedRuntime.functionSymbolName(
            listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("StringBuilder")),
            Identifier("build"),
        )
        val buildImport = Wasm.importFunction(
            moduleName = "env",
            entityName = buildSymbolName,
            identifier = buildSymbolName,
            params = listOf(WasmData.genericValueType),
            results = listOf(WasmData.genericValueType),
        )
        val (context2, buildClosure) = WasmClosures.compileCreateForFunction(
            tableIndex = WasmConstValue.TableEntryIndex(buildSymbolName),
            freeVariables = listOf(),
            context.addImport(buildImport).addTableEntry(buildImport.identifier),
        )


        val writeSymbolName = ShedRuntime.functionSymbolName(
            listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("StringBuilder")),
            Identifier("write"),
        )
        val writeImport = Wasm.importFunction(
            moduleName = "env",
            entityName = writeSymbolName,
            identifier = writeSymbolName,
            params = listOf(WasmData.genericValueType),
            results = listOf(WasmData.genericValueType),
        )
        val (context3, writeClosure) = WasmClosures.compileCreateForFunction(
            tableIndex = WasmConstValue.TableEntryIndex(writeSymbolName),
            freeVariables = listOf(),
            context2.addImport(writeImport).addTableEntry(writeImport.identifier),
        )

        val exports = listOf(
            Pair(Identifier("build"), buildClosure.get()),
            Pair(Identifier("write"), writeClosure.get()),
        )
        return Pair(context3, exports)
    }
}
