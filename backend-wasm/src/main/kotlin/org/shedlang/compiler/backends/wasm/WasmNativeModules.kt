package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.CompilerError
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.ast.NullSource
import org.shedlang.compiler.backends.ShedRuntime
import org.shedlang.compiler.backends.wasm.wasm.*
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmConstValue
import org.shedlang.compiler.backends.wasm.wasm.WasmFuncType
import org.shedlang.compiler.backends.wasm.wasm.WasmInstruction
import org.shedlang.compiler.backends.wasm.wasm.WasmParam
import org.shedlang.compiler.types.*

internal object WasmNativeModules {
    private val modules = mapOf<
        ModuleName,
        (WasmFunctionContext) -> Pair<WasmFunctionContext, List<Pair<Identifier, WasmInstruction.Folded>>>,
    >(
        listOf(Identifier("Core"), Identifier("Io")) to ::generateCoreIoModule,
    )

    fun moduleInitialisation(moduleName: ModuleName, type: ModuleType): (WasmFunctionContext) -> Pair<WasmFunctionContext, List<Pair<Identifier, WasmInstruction.Folded>>> {
        val init = modules[moduleName]
        if (init == null) {
            return { context ->
                generateModule(
                    moduleName = moduleName,
                    functions = type.fields.mapNotNull { (fieldName, field) ->
                        val fieldType = rawValue(field.type)
                        val parameterCount = when (fieldType) {
                            is FunctionType ->
                                fieldType.positionalParameters.size + fieldType.namedParameters.size
                            is StaticValueType ->
                                when (fieldType.value) {
                                    is OpaqueEffect ->
                                        null
                                    else ->
                                        throw CompilerError("unhandled fieldType: ${fieldType}", NullSource)
                                }
                            else ->
                                throw CompilerError("unhandled fieldType: ${fieldType}", NullSource)
                        }
                        if (parameterCount == null) {
                            null
                        } else {
                            // TODO: remove duplication of knowledge of signature
                            val wasmType = Wasm.T.funcType(
                                listOf(WasmData.closureEnvironmentPointerType) + List(parameterCount) { WasmData.genericValueType },
                                listOf(WasmData.genericValueType),
                            )
                            Pair(fieldName.value, wasmType)
                        }
                    },
                    context = context,
                )
            }
        } else {
            return init
        }
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
