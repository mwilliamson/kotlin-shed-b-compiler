package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmConstValue
import org.shedlang.compiler.backends.wasm.wasm.WasmImport
import org.shedlang.compiler.backends.wasm.wasm.WasmInstruction
import org.shedlang.compiler.types.FunctionType
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

            val operationParams = OperationParams(operationType = operationType)

            val (currentContext2, closure) = WasmClosures.compileCreate(
                functionName = functionName,
                freeVariables = listOf(),
                positionalParams = operationParams.positionalParams,
                namedParams = operationParams.namedParams,
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
                                params = listOf(Wasm.T.i32, Wasm.T.i32) + operationParams.paramTypes,
                                results = listOf(WasmData.genericValueType),
                            ),
                            tableIndex = operationHandlerFunction.get(),
                            // TODO: extract calling convention, ensure consistent order of named arguments
                            args = listOf(effectHandler.get(), operationHandlerContext.get()) + operationParams.args,
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
        val context2 = compileCallPush(effect = effect, context = context)
        val (context3, effectHandler) = context2.addLocal("effectHandler")
        val context4 = context3.addInstruction(effectHandler.set())

        val operationTypes = effect.operations.map { (_, operationType) -> operationType }

        val context5 = operationHandlers.foldIndexed(context4) { operationIndex, currentContext, operationHandler ->
            val operationType = operationTypes[operationIndex]

            val operationParams = OperationParams(operationType = operationType)

            // TODO: uniquify name properly
            val outerHandlerName = "operation_handler_outer_" + freshNodeId()
            val currentContext2 = currentContext.mergeGlobalContext(WasmFunctionContext.initial()
                .let {
                    WasmClosures.compileCall(
                        closurePointer = Wasm.I.localGet("operationHandler"),
                        positionalArguments = operationParams.positionalArgs,
                        namedArguments = operationParams.namedArgs,
                        context = it,
                    )
                }
                .toFunctionInGlobalContext(
                    identifier = outerHandlerName,
                    params = listOf(Wasm.param("effectHandler", Wasm.T.i32), Wasm.param("operationHandler", Wasm.T.i32)) + operationParams.params,
                    results = listOf(WasmData.genericValueType),
                ))

            compileCallSetOperationHandler(
                effectHandler = effectHandler,
                operationIndex = operationIndex,
                function = Wasm.I.i32Const(WasmConstValue.TableEntryIndex(outerHandlerName)),
                effectContext = operationHandler.get(),
                context = currentContext2,
            )
        }

        return compileBody(context5)
    }

    private class OperationParams(operationType: FunctionType) {
        val positionalParams = operationType.positionalParameters.mapIndexed { paramIndex, param ->
            Wasm.param("param_${paramIndex}", WasmData.genericValueType)
        }

        val namedParams = operationType.namedParameters.map { (paramName, paramType) ->
            paramName to Wasm.param("param_${paramName.value}", WasmData.genericValueType)
        }

        val params = positionalParams + namedParams.map { (_, param) -> param }

        val paramNames = params.map { param -> param.identifier }

        val paramTypes = params.map { param -> param.type }

        val positionalArgs = positionalParams.map { param -> Wasm.I.localGet(param.identifier) }

        val namedArgs = namedParams.map { (name, param) -> name to Wasm.I.localGet(param.identifier) }

        val args = paramNames.map { paramName -> Wasm.I.localGet(paramName) }
    }



    private object Naming {
        internal val push = "shed_effects_push"
        internal val setOperationHandler = "shed_effects_set_operation_handler"
        internal val findEffectHandler = "shed_effects_find_effect_handler"
        internal val getOperationHandler = "shed_effects_get_operation_handler"
        internal val operationHandlerGetFunction = "shed_effects_operation_handler_get_function"
        internal val operationHandlerGetContext = "shed_effects_operation_handler_get_context"
    }

    private fun compileCallPush(effect: UserDefinedEffect, context: WasmFunctionContext): WasmFunctionContext {
        return context.addInstruction(Wasm.I.call(
            identifier = Naming.push,
            args = listOf(Wasm.I.i32Const(effect.definitionId), Wasm.I.i32Const(effect.operations.size)),
        ))
    }

    private fun compileCallSetOperationHandler(
        effectHandler: WasmLocalRef,
        operationIndex: Int,
        function: WasmInstruction.Folded,
        effectContext: WasmInstruction.Folded,
        context: WasmFunctionContext,
    ): WasmFunctionContext {
        return context.addInstruction(Wasm.I.call(
            identifier = Naming.setOperationHandler,
            args = listOf(effectHandler.get(), Wasm.I.i32Const(operationIndex), function, effectContext),
        ))
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
                entityName = Naming.push,
                identifier = Naming.push,
                params = listOf(Wasm.T.i32, Wasm.T.i32),
                results = listOf(Wasm.T.i32),
            ),
            Wasm.importFunction(
                moduleName = "env",
                entityName = Naming.setOperationHandler,
                identifier = Naming.setOperationHandler,
                params = listOf(Wasm.T.i32, Wasm.T.i32, Wasm.T.i32, Wasm.T.i32),
                results = listOf(),
            ),
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

