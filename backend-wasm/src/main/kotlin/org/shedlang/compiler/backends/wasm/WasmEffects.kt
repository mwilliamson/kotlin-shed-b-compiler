package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmImport
import org.shedlang.compiler.backends.wasm.wasm.WasmInstruction
import org.shedlang.compiler.types.UserDefinedEffect
internal object WasmEffects {
    fun compileEffectDefine(effect: UserDefinedEffect, context: WasmFunctionContext): WasmFunctionContext {
        val layout = WasmObjects.effectLayout(effect)
        val (context2, effectObj) = malloc(effect.name.value, layout, context)

        val fieldValues = mutableListOf<Pair<Identifier, WasmInstruction.Folded>>()

        val context3 = effect.operations.entries.fold(context2) { currentContext, (operationName, operationType) ->
            // TODO: uniquify name properly
            val functionName = operationName.value + freshNodeId()

            // TODO: extract this? Presumably duplicated somewhere...
            val operationIndex = effect.operations.keys.sorted().indexOf(operationName)

            val positionalParams = operationType.positionalParameters.mapIndexed { paramIndex, param ->
                Wasm.param("param_${paramIndex}", WasmData.genericValueType)
            }

            val namedParams = operationType.namedParameters.map { (paramName, paramType) ->
                paramName to Wasm.param("param_${paramName.value}", WasmData.genericValueType)
            }

            val paramNames = positionalParams.map { param -> param.identifier } + namedParams.map { (_, param) -> param.identifier }

            val (currentContext2, closure) = WasmClosures.compileCreate(
                functionName = functionName,
                freeVariables = listOf(),
                positionalParams = positionalParams,
                namedParams = namedParams,
                compileBody = { bodyContext ->
                    val (bodyContext2, effectHandler) = bodyContext.addLocal("effectHandler")
                    val (bodyContext3, operationHandler) = bodyContext2.addLocal("operationHandler")
                    val (bodyContext4, operationHandlerFunction) = bodyContext3.addLocal("operationHandlerFunction")
                    val (bodyContext5, operationHandlerContext) = bodyContext4.addLocal("operationHandlerContext")

                    bodyContext5
                        .let {
                            compileCallFindEffectHandler(
                                effect = effect,
                                context = it,
                            )
                        }
                        .addInstruction(effectHandler.set())
                        .let {
                            compileCallGetOperationHandler(
                                effectHandler = effectHandler,
                                operationIndex = operationIndex,
                                context = it,
                            )
                        }
                        .addInstruction(operationHandler.set())
                        .let {
                            compileCallOperationHandlerGetFunction(
                                operationHandler = operationHandler,
                                context = it,
                            )
                        }
                        .addInstruction(operationHandlerFunction.set())
                        .let {
                            compileCallOperationHandlerGetContext(
                                operationHandler = operationHandler,
                                context = it,
                            )
                        }
                        .addInstruction(operationHandlerContext.set())
                        .addInstruction(Wasm.I.callIndirect(
                            type = Wasm.T.funcType(
                                params = paramNames.map { WasmData.genericValueType },
                                results = listOf(WasmData.genericValueType),
                            ),
                            tableIndex = operationHandlerFunction.get(),
                            // TODO: extract calling convention, ensure consistent order of named arguments
                            args = paramNames.map { paramName -> Wasm.I.localGet(paramName) },
                        ))
                },
                context = currentContext
            )

            fieldValues.add(Pair(operationName, closure.get()))

            currentContext2
        }

        val context4 = WasmObjects.compileObjectStore(
            objectPointer = effectObj.get(),
            layout = layout,
            fieldValues = fieldValues,
            context = context3
        )
        return context4.addInstruction(effectObj.get())
    }

    fun compileEffectHandle(
        effect: UserDefinedEffect,
        compileBody: (WasmFunctionContext) -> WasmFunctionContext,
        operationHandlers: List<WasmLocalRef>,
        context: WasmFunctionContext
    ): WasmFunctionContext {
        return compileBody(context)
    }

    private object Naming {
        internal val findEffectHandler = "shed_effects_find_effect_handler"
        internal val getOperationHandler = "shed_effects_get_operation_handler"
        internal val operationHandlerGetFunction = "shed_effects_operation_handler_get_function"
        internal val operationHandlerGetContext = "shed_effects_operation_handler_get_context"
    }

    private fun compileCallFindEffectHandler(effect: UserDefinedEffect, context: WasmFunctionContext): WasmFunctionContext {
        return context.addInstruction(Wasm.I.call(
            identifier = Naming.findEffectHandler,
            args = listOf(Wasm.I.i32Const(effect.definitionId)),
        ))
    }

    private fun compileCallGetOperationHandler(
        effectHandler: WasmLocalRef,
        operationIndex: Int,
        context: WasmFunctionContext,
    ): WasmFunctionContext {
        return context.addInstruction(Wasm.I.call(
            identifier = Naming.getOperationHandler,
            args = listOf(effectHandler.get(), Wasm.I.i32Const(operationIndex)),
        ))
    }

    private fun compileCallOperationHandlerGetFunction(operationHandler: WasmLocalRef, context: WasmFunctionContext): WasmFunctionContext {
        return context.addInstruction(Wasm.I.call(
            identifier = Naming.operationHandlerGetFunction,
            args = listOf(operationHandler.get()),
        ))
    }

    private fun compileCallOperationHandlerGetContext(operationHandler: WasmLocalRef, context: WasmFunctionContext): WasmFunctionContext {
        return context.addInstruction(Wasm.I.call(
            identifier = Naming.operationHandlerGetContext,
            args = listOf(operationHandler.get()),
        ))
    }

    fun imports(): List<WasmImport> {
        return listOf(
            Wasm.importFunction(
                moduleName = "env",
                entityName = Naming.findEffectHandler,
                identifier = Naming.findEffectHandler,
                params = listOf(Wasm.T.i32),
                results = listOf(Wasm.T.i32),
            ),
            Wasm.importFunction(
                moduleName = "env",
                entityName = Naming.getOperationHandler,
                identifier = Naming.getOperationHandler,
                params = listOf(Wasm.T.i32, Wasm.T.i32),
                results = listOf(Wasm.T.i32),
            ),
            Wasm.importFunction(
                moduleName = "env",
                entityName = Naming.operationHandlerGetFunction,
                identifier = Naming.operationHandlerGetFunction,
                params = listOf(Wasm.T.i32),
                results = listOf(Wasm.T.i32),
            ),
            Wasm.importFunction(
                moduleName = "env",
                entityName = Naming.operationHandlerGetContext,
                identifier = Naming.operationHandlerGetContext,
                params = listOf(Wasm.T.i32),
                results = listOf(Wasm.T.i32),
            ),
        )
    }
}

