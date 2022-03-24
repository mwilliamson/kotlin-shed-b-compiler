package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.backends.ShedRuntime
import org.shedlang.compiler.backends.wasm.wasm.*
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmConstValue
import org.shedlang.compiler.backends.wasm.wasm.WasmFuncType
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
        listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("Strings")) to ::generateStdlibPlatformStringsModule,
    )

    fun moduleInitialisation(moduleName: ModuleName) = modules[moduleName]

    private fun generateCoreCastModule(
        context: WasmFunctionContext,
    ): Pair<WasmFunctionContext, List<Pair<Identifier, WasmInstruction.Folded>>> {

        return generateModule(
            moduleName = listOf(Identifier("Core"), Identifier("Cast")),
            functions = listOf(
                Pair("cast", Wasm.T.funcType(listOf(WasmData.genericValueType, WasmData.genericValueType, WasmData.genericValueType), listOf(WasmData.genericValueType))),
            ),
            context = context,
        )
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
            context = context,
        )
        val exports = listOf(
            Pair(Identifier("print"), closure.get())
        )
        return Pair(context2, exports)
    }

    private fun generateCoreIntToStringModule(
        context: WasmFunctionContext,
    ): Pair<WasmFunctionContext, List<Pair<Identifier, WasmInstruction.Folded>>> {

        return generateModule(
            moduleName = listOf(Identifier("Core"), Identifier("IntToString")),
            functions = listOf(
                Pair("intToString", Wasm.T.funcType(listOf(WasmData.genericValueType, WasmData.genericValueType), listOf(WasmData.genericValueType))),
            ),
            context = context,
        )
    }

    private fun generateStdlibPlatformProcessModule(
        context: WasmFunctionContext,
    ): Pair<WasmFunctionContext, List<Pair<Identifier, WasmInstruction.Folded>>> {

        return generateModule(
            moduleName = listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("Process")),
            functions = listOf(
                Pair("args", Wasm.T.funcType(listOf(), listOf(WasmData.genericValueType))),
            ),
            context = context,
        )
    }

    private fun generateStdlibPlatformStringBuilderModule(
        context: WasmFunctionContext,
    ): Pair<WasmFunctionContext, List<Pair<Identifier, WasmInstruction.Folded>>> {

        return generateModule(
            moduleName = listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("StringBuilder")),
            functions = listOf(
                Pair("build", Wasm.T.funcType(listOf(), listOf(WasmData.genericValueType))),
                Pair("write", Wasm.T.funcType(listOf(), listOf(WasmData.genericValueType))),
            ),
            context = context,
        )
    }

    private fun generateStdlibPlatformStringsModule(
        context: WasmFunctionContext,
    ): Pair<WasmFunctionContext, List<Pair<Identifier, WasmInstruction.Folded>>> {

        return generateModule(
            moduleName = listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("Strings")),
            functions = listOf(
                // TODO: proper types of imports
                Pair("dropLeftUnicodeScalars", Wasm.T.funcType(params = listOf(), results = listOf(WasmData.genericValueType))),
                Pair("next", Wasm.T.funcType(params = listOf(), results = listOf(WasmData.genericValueType))),
                Pair("replace", Wasm.T.funcType(params = listOf(), results = listOf(WasmData.genericValueType))),
                Pair("slice", Wasm.T.funcType(params = listOf(), results = listOf(WasmData.genericValueType))),
                Pair("substring", Wasm.T.funcType(params = listOf(), results = listOf(WasmData.genericValueType))),
                Pair("unicodeScalarCount", Wasm.T.funcType(params = listOf(), results = listOf(WasmData.genericValueType))),
                Pair("unicodeScalarToInt", Wasm.T.funcType(params = listOf(), results = listOf(WasmData.genericValueType))),
                Pair("unicodeScalarToHexString", Wasm.T.funcType(params = listOf(), results = listOf(WasmData.genericValueType))),
                Pair("unicodeScalarToString", Wasm.T.funcType(params = listOf(), results = listOf(WasmData.genericValueType))),
            ),
            context = context,
        )
    }

    private fun generateModule(
        moduleName: ModuleName,
        functions: List<Pair<String, WasmFuncType>>,
        context: WasmFunctionContext,
    ): Pair<WasmFunctionContext, List<Pair<Identifier, WasmInstruction.Folded>>> {
        var currentContext = context
        val exports = mutableListOf<Pair<Identifier, WasmInstruction.Folded>>()

        for ((functionName, functionType) in functions) {
            val symbolName = ShedRuntime.functionSymbolName(
                moduleName,
                Identifier(functionName),
            )
            val import = Wasm.importFunction(
                moduleName = "env",
                entityName = symbolName,
                identifier = symbolName,
                params = functionType.params,
                results = functionType.results,
            )
            val (newContext, closure) = WasmClosures.compileCreateForFunction(
                tableIndex = WasmConstValue.TableEntryIndex(symbolName),
                freeVariables = listOf(),
                currentContext.addImport(import).addTableEntry(import.identifier),
            )
            currentContext = newContext
            exports.add(Pair(Identifier(functionName), closure.get()))
        }
        return Pair(currentContext, exports)
    }
}
