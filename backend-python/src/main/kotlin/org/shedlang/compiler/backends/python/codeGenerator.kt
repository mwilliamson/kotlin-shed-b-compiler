package org.shedlang.compiler.backends.python

import org.shedlang.compiler.Module
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.*
import org.shedlang.compiler.backends.python.ast.*
import org.shedlang.compiler.nullableToList
import org.shedlang.compiler.types.Discriminator
import org.shedlang.compiler.types.EmptyFunctionType
import org.shedlang.compiler.types.ShapeType

// TODO: check that builtins aren't renamed
// TODO: check imports aren't renamed

internal fun generateCode(
    module: Module.Shed,
    moduleSet: ModuleSet
): PythonModuleNode {
    val isPackage = isPackage(moduleSet, module.name)
    val context = CodeGenerationContext(
        inspector = ModuleCodeInspector(module),
        moduleName = module.name,
        isPackage = isPackage,
    )
    return generateCode(module.node, context)
}

internal fun isPackage(moduleSet: ModuleSet, moduleName: ModuleName): Boolean {
    return moduleSet.modules.any { module ->
        module.name.size > moduleName.size && module.name.subList(0, moduleName.size) == moduleName
    }
}

internal class CodeGenerationContext(
    val inspector: CodeInspector,
    val moduleName: ModuleName,
    val isPackage: Boolean,
    private val nodeNames: MutableMap<Int, String> = mutableMapOf(),
    private val namesInScope: MutableSet<String> = mutableSetOf(),
) {
    fun enterScope(): CodeGenerationContext {
        return CodeGenerationContext(
            inspector = inspector,
            moduleName = moduleName,
            isPackage = isPackage,
            nodeNames = nodeNames,
            namesInScope = namesInScope.toMutableSet(),
        )
    }

    fun freshName(name: String? = null, expression: ExpressionNode? = null): String {
        return generateName(if (name != null) {
            name
        } else if (expression is FieldAccessNode) {
            expression.fieldName.identifier.value
        } else {
            "anonymous"
        })
    }

    fun name(node: VariableBindingNode): String {
        return name(node.nodeId, node.name)
    }

    fun name(node: ReferenceNode): String {
        return name(inspector.resolve(node))
    }

    private fun name(nodeId: Int, name: Identifier): String {
        if (!nodeNames.containsKey(nodeId)) {
            val pythonName = pythoniseName(name)
            nodeNames[nodeId] = pythonName
        }
        return nodeNames[nodeId]!!
    }

    private fun generateName(originalName: String): String {
        val name = uniquifyName(originalName)
        namesInScope.add(name)
        return name
    }

    private fun uniquifyName(base: String): String {
        var index = 0
        var name = base
        while (namesInScope.contains(name)) {
            index++
            name = base + "_" + index
        }
        return name
    }
}

internal fun generateCode(node: ModuleNode, context: CodeGenerationContext): PythonModuleNode {
    val imports = node.imports.flatMap{ import -> generateImportCode(import, context) }
    val body = node.body.flatMap{ child -> generateModuleStatementCode(child, context) }
    return PythonModuleNode(
        imports + body,
        source = NodeSource(node)
    )
}

private fun generateImportCode(node: ImportNode, context: CodeGenerationContext): List<PythonStatementNode> {
    // TODO: assign names properly using context
    val source = NodeSource(node)

    val pythonPackageName = node.path.parts.take(node.path.parts.size - 1).map { part -> part.value }
    val module = when (node.path.base) {
        ImportPathBase.Relative -> if (context.isPackage) { ".." } else { "." } + pythonPackageName.joinToString(".")
        ImportPathBase.Absolute -> (listOf(topLevelPythonPackageName) + pythonPackageName).joinToString(".")
    }
    val importName = node.path.parts.last().value

    val target = node.target
    if (target is TargetNode.Variable) {
        return listOf(
            PythonImportFromNode(
                module = module,
                names = listOf(importName to pythoniseName(target.name)),
                source = source
            )
        )
    } else {
        val targetName = context.freshName("import_target")
        return listOf(
            PythonImportFromNode(
                module = module,
                names = listOf(importName to targetName),
                source = source
            )
        ) + generateTargetAssignment(
            target,
            PythonVariableReferenceNode(targetName, source = NodeSource(target)),
            source = NodeSource(target),
            context = context
        )
    }
}

internal fun generateModuleStatementCode(node: ModuleStatementNode, context: CodeGenerationContext): List<PythonStatementNode> {
    return node.accept(object : ModuleStatementNode.Visitor<List<PythonStatementNode>> {
        override fun visit(node: EffectDefinitionNode): List<PythonStatementNode> = generateCodeForEffectDefinition(node, context)
        override fun visit(node: TypeAliasNode): List<PythonStatementNode> = listOf()
        override fun visit(node: ShapeNode) = listOf(generateCodeForShape(node, context))
        override fun visit(node: UnionNode): List<PythonStatementNode> = generateCodeForUnion(node, context)
        override fun visit(node: FunctionDefinitionNode) = listOf(generateCodeForFunctionDefinition(node, context))
        override fun visit(node: ValNode) = generateCode(node, context)
        override fun visit(node: VarargsDeclarationNode) = generateCodeForVarargsDeclaration(node, context)
    })
}

private fun generateCodeForEffectDefinition(node: EffectDefinitionNode, context: CodeGenerationContext): List<PythonStatementNode> {
    val source = NodeSource(node)

    val className = context.name(node)
    val effectId = node.nodeId.toBigInteger()
    return listOf(
        PythonClassNode(
            name = className,
            body = listOf(
                assign("_effect_id", PythonIntegerLiteralNode(effectId, source = source), source = source)
            ) + node.operations.flatMap { operation ->
                val operationSource = NodeSource(operation)

                val operationDefinition = PythonFunctionNode(
                    decorators = listOf(PythonVariableReferenceNode("staticmethod", source = operationSource)),
                    name = pythoniseName(operation.name),
                    // TODO: proper support for *args, **kwargs? Or explicitly list all args?
                    parameters = listOf("*operation_args, **operation_kwargs"),
                    body = listOf(
                        PythonReturnNode(
                            expression = PythonFunctionCallNode(
                                function = PythonVariableReferenceNode("_effect_handler_call", source = operationSource),
                                arguments = listOf(
                                    PythonIntegerLiteralNode(effectId, source = operationSource),
                                    PythonStringLiteralNode(pythoniseName(operation.name), source = operationSource),
                                    PythonVariableReferenceNode("*operation_args", source = operationSource),
                                    PythonVariableReferenceNode("**operation_kwargs", source = operationSource),
                                ),
                                keywordArguments = listOf(),
                                source = operationSource
                            ),
                            source = operationSource
                        )
                    ),
                    source = operationSource
                )

                listOf(
                    operationDefinition
                )
            },
            source = source
        )
    )
}

private fun <T: Node> generateCodeForBranchingExpression(
    node: T,
    context: CodeGenerationContext,
    generateStatements: (T, CodeGenerationContext, ReturnValue) -> List<PythonStatementNode>
): GeneratedExpression {
    val targetName = context.freshName()

    fun returnValue(expression: PythonExpressionNode, source: Source): List<PythonStatementNode> {
        return listOf(assign(targetName, expression, source = source))
    }

    val statements = generateStatements(
        node,
        context,
        ::returnValue
    )

    val reference = PythonVariableReferenceNode(targetName, source = NodeSource(node))

    return GeneratedExpression(
        reference,
        statements = statements,
        spilled = true
    )
}

private fun generateCodeForHandle(node: HandleNode, context: CodeGenerationContext, returnValue: ReturnValue): List<PythonStatementNode> {
    val handleSource = NodeSource(node)
    val resultVariableName = context.freshName("result")

    val body = listOf(
        assign(resultVariableName, PythonNoneLiteralNode(source = handleSource), source = handleSource)
    ) + generateBlockCode(node.body, context, returnValue = { pythonExpressionNode, source ->
        listOf(
            assign(resultVariableName, pythonExpressionNode, source = source)
        )
    }) + listOf(
        PythonExpressionStatementNode(
            PythonFunctionCallNode(
                function = PythonVariableReferenceNode("_effect_handler_discard", source = handleSource),
                arguments = listOf(),
                keywordArguments = listOf(),
                source = handleSource
            ),
            source = handleSource
        )
    ) + returnValue(
            PythonVariableReferenceNode(resultVariableName, source = handleSource),
            handleSource,
        )

    val exitValueName = context.freshName("exit_value")
    val effectHandlerName = context.freshName("effect_handler")

    val effectHandlerPush = GeneratedCode.flatten(node.handlers.map { handler ->
        generateExpressionCode(handler.function, context)
    }).flatMap { operationHandlers ->
        val initialState = node.initialState
        if (initialState == null) {
            GeneratedCode.pure(listOf())
        } else {
            generateExpressionCode(initialState, context).pureMap { initialState ->
                listOf(
                    setState(initialState, source = handleSource)
                )
            }
        }.pureMap { Pair(operationHandlers, it) }

    }.toStatements { (operationHandlers, setState) ->
        val pushEffectHandler = assign(
            effectHandlerName,
            PythonFunctionCallNode(
                function = PythonVariableReferenceNode("_effect_handler_push", source = handleSource),
                arguments = listOf(
                    generateCode(node.effect, context),
                    PythonFunctionCallNode(
                        // TODO: handle shed variables called dict
                        function = PythonVariableReferenceNode("dict", source = handleSource),
                        arguments = listOf(),
                        keywordArguments = node.handlers.mapIndexed { operationIndex, handler ->
                            val handlerSource = NodeSource(handler)

                            pythoniseName(handler.operationName) to PythonFunctionCallNode(
                                function = PythonVariableReferenceNode("_effect_handler_create_operation_handler", source = handlerSource),
                                arguments = listOf(
                                    operationHandlers[operationIndex]
                                ),
                                keywordArguments = listOf(),
                                source = handlerSource
                            )
                        },
                        source = handleSource
                    )
                ),
                keywordArguments = listOf(),
                source = handleSource
            ),
            source = handleSource
        )

        setState + listOf(pushEffectHandler)
    }

    val tryStatement = PythonTryNode(
        body = body,
        exceptClauses = listOf(
            PythonExceptNode(
                exceptionType = PythonAttributeAccessNode(
                    receiver = PythonVariableReferenceNode(effectHandlerName, source = handleSource),
                    attributeName = "Exit",
                    source = handleSource
                ),
                target = exitValueName,
                body = returnValue(
                    PythonAttributeAccessNode(
                        receiver = PythonVariableReferenceNode(exitValueName, source = handleSource),
                        // TODO: extract string constant
                        attributeName = "value",
                        source = handleSource
                    ),
                    handleSource
                ),
                source = handleSource
            )
        ),
        elseClause = listOf(),
        source = handleSource
    )

    return effectHandlerPush + listOf(tryStatement)
}

private fun setState(state: PythonExpressionNode, source: NodeSource): PythonExpressionStatementNode {
    return PythonExpressionStatementNode(
        PythonFunctionCallNode(
            function = PythonVariableReferenceNode("_effect_handler_set_state", source = source),
            arguments = listOf(state),
            keywordArguments = listOf(),
            source = source,
        ),
        source = source,
    )
}

private fun generateCodeForShape(node: ShapeBaseNode, context: CodeGenerationContext): PythonClassNode {
    val shapeFields = context.inspector.shapeFields(node)
    val variableFields = shapeFields.filter { field -> !field.isConstant }

    val shapeSource = NodeSource(node)

    val init = if (variableFields.isEmpty()) {
        listOf()
    } else {
        listOf(
            PythonFunctionNode(
                name = "__init__",
                parameters = listOf("self") + variableFields.map { field -> pythoniseName(field.name) },
                body = variableFields.map { field ->
                    PythonAssignmentNode(
                        target = PythonAttributeAccessNode(
                            receiver = PythonVariableReferenceNode("self", source = shapeSource),
                            attributeName = pythoniseName(field.name),
                            source = shapeSource
                        ),
                        expression = PythonVariableReferenceNode(pythoniseName(field.name), source = shapeSource),
                        source = shapeSource
                    )
                },
                source = shapeSource
            )
        )
    }

    val fieldsClass = generateFieldsClass(node, shapeSource, context)

    val nameAssignment = assign(
        "name",
        PythonStringLiteralNode(node.name.value, source = shapeSource),
        source = shapeSource,
    )

    val tagValue = context.inspector.shapeTagValue(node)
    val tagValueAssignment = if (tagValue == null) {
        listOf()
    } else {
        listOf(
            PythonAssignmentNode(
                PythonVariableReferenceNode(tagValueAttributeName, source = shapeSource),
                PythonStringLiteralNode(tagValue.value.value, source = shapeSource),
                source = shapeSource
            )
        )
    }

    val body = shapeFields.mapNotNull { field ->
        val fieldValue = field.value

        val value = when (fieldValue) {
            null ->
                null

            is FieldValue.Expression ->
                generateExpressionCode(fieldValue.expression, context).pureExpression()
        }
        if (value == null) {
            null
        } else {
            PythonAssignmentNode(
                PythonVariableReferenceNode(pythoniseName(field.name), source = shapeSource),
                value,
                source = shapeSource
            )
        }
    } + init + listOf(fieldsClass, nameAssignment) + tagValueAssignment
    return PythonClassNode(
        // TODO: test renaming
        name = context.name(node),
        body = body,
        source = shapeSource
    )
}

private fun generateFieldsClass(node: ShapeBaseNode, shapeSource: NodeSource, context: CodeGenerationContext): PythonClassNode {
    val shapeFields = context.inspector.shapeFields(node)

    return PythonClassNode(
        // TODO: handle constant field also named fields
        name = "fields",
        body = shapeFields.map { field ->
            val fieldNode = node.fields
                .find { fieldNode -> fieldNode.name == field.name }
            val fieldSource = NodeSource(fieldNode ?: node)

            PythonAssignmentNode(
                target = PythonVariableReferenceNode(
                    name = pythoniseName(field.name),
                    source = fieldSource
                ),
                expression = generateFieldObject(node = node, field = field, fieldSource = fieldSource, context = context),
                source = fieldSource
            )
        },
        source = shapeSource
    )
}

private fun generateFieldObject(node: ShapeBaseNode, field: FieldInspector, fieldSource: NodeSource, context: CodeGenerationContext): PythonFunctionCallNode {
    val get = PythonLambdaNode(
        parameters = listOf("value"),
        body = PythonAttributeAccessNode(
            receiver = PythonVariableReferenceNode(
                "value",
                source = fieldSource
            ),
            attributeName = pythoniseName(field.name),
            source = fieldSource
        ),
        source = fieldSource
    )

    val name = PythonStringLiteralNode(
        value = field.name.value,
        source = fieldSource
    )

    val update = generateUpdateFunction(node, updatedField = field, fieldSource = fieldSource, context = context)

    return PythonFunctionCallNode(
        function = PythonVariableReferenceNode(
            name = "_create_shape_field",
            source = fieldSource
        ),
        arguments = listOf(),
        keywordArguments = listOf(
            "get" to get,
            "name" to name,
            "update" to update,
        ),
        source = fieldSource
    )
}

private fun generateUpdateFunction(node: ShapeBaseNode, updatedField: FieldInspector, fieldSource: NodeSource, context: CodeGenerationContext): PythonLambdaNode {
    return PythonLambdaNode(
        parameters = listOf("new_field_value", "existing_object"),
        body = PythonFunctionCallNode(
            function = PythonVariableReferenceNode(context.name(node), source = fieldSource),
            arguments = listOf(),
            keywordArguments = context.inspector.shapeFields(node).map { field ->
                val fieldValue = if (field.name == updatedField.name) {
                    PythonVariableReferenceNode(
                        "new_field_value",
                        source = fieldSource
                    )
                } else {
                    PythonAttributeAccessNode(
                        receiver = PythonVariableReferenceNode(
                            "existing_object",
                            source = fieldSource
                        ),
                        attributeName = pythoniseName(field.name),
                        source = fieldSource
                    )
                }
                pythoniseName(field.name) to fieldValue
            },
            source = fieldSource,
        ),
        source = fieldSource
    )
}

private fun generateCodeForUnion(node: UnionNode, context: CodeGenerationContext): List<PythonStatementNode> {
    val source = NodeSource(node)
    val assignment = assign(
        targetName = pythoniseName(node.name),
        expression = PythonNoneLiteralNode(source = source),
        source = source
    )
    return listOf(assignment) + node.members.map { member -> generateCodeForShape(member, context) }
}

private fun generateCodeForVarargsDeclaration(node: VarargsDeclarationNode, context: CodeGenerationContext): List<PythonStatementNode> {
    val source = NodeSource(node)
    val cons = generateExpressionCode(node.cons, context)
    val nil = generateExpressionCode(node.nil, context)
    val assignment = assign(
        targetName = pythoniseName(node.name),
        expression = PythonFunctionCallNode(
            function = PythonVariableReferenceNode("_varargs", source = source),
            arguments = listOf(cons.pureExpression(), nil.pureExpression()),
            keywordArguments = listOf(),
            source = source
        ),
        source = source
    )
    return listOf(assignment)
}

private fun generateCodeForFunctionDefinition(node: FunctionDefinitionNode, context: CodeGenerationContext): PythonFunctionNode {
    return generateFunction(context.name(node), node, context)
}

typealias ReturnValue = (PythonExpressionNode, Source) -> List<PythonStatementNode>

private fun generateFunction(name: String, node: FunctionNode, context: CodeGenerationContext): PythonFunctionNode {
    val bodyContext = context.enterScope()
    val parameters = generateParameters(node, bodyContext)

    fun returnValue(expression: PythonExpressionNode, source: Source): List<PythonStatementNode> {
        return listOf(
            PythonReturnNode(
                expression = expression,
                source = source
            )
        )
    }

    val body = generateBlockCode(
        node.body,
        bodyContext,
        returnValue = ::returnValue
    )

    return PythonFunctionNode(
        // TODO: test renaming
        name = name,
        // TODO: test renaming
        parameters = parameters,
        body = body,
        source = NodeSource(node)
    )
}

private fun generateParameters(function: FunctionNode, context: CodeGenerationContext) =
    function.parameters.map({ parameter -> context.name(parameter) }) +
        function.namedParameters.map({ parameter -> context.name(parameter) })

private fun generateBlockCode(
    block: Block?,
    context: CodeGenerationContext,
    returnValue: ReturnValue
): List<PythonStatementNode> {
    if (block == null) {
        return listOf()
    } else {
        return block.statements.flatMap { statement ->
            generateCodeForFunctionStatement(statement, context, returnValue = returnValue)
        }
    }
}

internal fun generateCodeForFunctionStatement(
    node: FunctionStatementNode,
    context: CodeGenerationContext,
    returnValue: ReturnValue
): List<PythonStatementNode> {
    return node.accept(object : FunctionStatementNode.Visitor<List<PythonStatementNode>> {
        override fun visit(node: ExpressionStatementNode): List<PythonStatementNode> {
            return generateCode(node, context, returnValue = returnValue)
        }

        override fun visit(node: ResumeNode): List<PythonStatementNode> {
            val newState = node.newState

            if (newState == null) {
                return generateStatementCodeForExpression(
                    node.expression,
                    context,
                    returnValue = returnValue,
                    source = NodeSource(node)
                )
            } else {
                val valueVariableName = context.freshName("value")
                val newStateVariableName = context.freshName("new_state")
                val valueStatements = generateStatementCodeForExpression(
                    node.expression,
                    context,
                    returnValue = { returnValue, returnSource ->
                        listOf(assign(valueVariableName, returnValue, source = returnSource))
                    },
                    source = NodeSource(node)
                )
                val newStateStatements = generateStatementCodeForExpression(
                    newState,
                    context,
                    returnValue = { returnValue, returnSource ->
                        listOf(assign(newStateVariableName, returnValue, source = returnSource))
                    },
                    source = NodeSource(node),
                )
                val setStateStatement = setState(
                    PythonVariableReferenceNode(newStateVariableName, source = NodeSource(node)),
                    source = NodeSource(node),
                )
                val returnStatement = PythonReturnNode(
                    expression = PythonVariableReferenceNode(valueVariableName, source = NodeSource(node)),
                    source = NodeSource(node),
                )
                return valueStatements + newStateStatements + listOf(setStateStatement, returnStatement)
            }
        }

        override fun visit(node: ValNode): List<PythonStatementNode> {
            return generateCode(node, context)
        }

        override fun visit(node: FunctionDefinitionNode): List<PythonStatementNode> {
            return listOf(generateCodeForFunctionDefinition(node, context))
        }

        override fun visit(node: ShapeNode): List<PythonStatementNode> {
            return listOf(generateCodeForShape(node, context))
        }

        override fun visit(node: EffectDefinitionNode): List<PythonStatementNode> {
            return generateCodeForEffectDefinition(node, context)
        }
    })
}

private fun generateCode(
    node: ExpressionStatementNode,
    context: CodeGenerationContext,
    returnValue: ReturnValue
): List<PythonStatementNode> {
    fun expressionReturnValue(expression: PythonExpressionNode, source: Source): List<PythonStatementNode> {
        return when (node.type) {
            ExpressionStatementNode.Type.TAILREC,
            ExpressionStatementNode.Type.VALUE ->
                returnValue(expression, source)

            ExpressionStatementNode.Type.NO_VALUE ->
                listOf(
                    PythonExpressionStatementNode(expression, source)
                )

            ExpressionStatementNode.Type.EXIT ->
                listOf(
                    PythonExpressionStatementNode(
                        PythonFunctionCallNode(
                            function = PythonVariableReferenceNode("_effect_handler_exit", source = source),
                            arguments = listOf(expression),
                            keywordArguments = listOf(),
                            source = source
                        ),
                        source = source
                    )
                )
        }
    }

    return generateStatementCodeForExpression(
        node.expression,
        context,
        returnValue = ::expressionReturnValue,
        source = NodeSource(node)
    )
}

private fun generateCode(node: ValNode, context: CodeGenerationContext): List<PythonStatementNode> {
    fun expressionReturnValue(expression: PythonExpressionNode, source: Source): List<PythonStatementNode> {
        return generateTargetAssignment(node.target, expression, source, context)
    }

    return generateStatementCodeForExpression(
        node.expression,
        context,
        returnValue = ::expressionReturnValue,
        source = NodeSource(node)
    )
}

private fun generateTargetAssignment(
    shedTarget: TargetNode,
    pythonExpression: PythonExpressionNode,
    source: Source,
    context: CodeGenerationContext
): List<PythonStatementNode> {
    val (pythonTarget, statements) = generateTargetCode(shedTarget, context)
    val assignment = PythonAssignmentNode(
        target = pythonTarget,
        expression = pythonExpression,
        source = source
    )
    return listOf(assignment) + statements
}


internal fun generateTargetCode(
    shedTarget: TargetNode,
    context: CodeGenerationContext
): Pair<PythonExpressionNode, List<PythonStatementNode>> {
    val source = NodeSource(shedTarget)
    return when (shedTarget) {
        is TargetNode.Ignore ->
            Pair(
                PythonVariableReferenceNode(
                    name = context.freshName("ignore"),
                    source = source
                ),
                listOf()
            )

        is TargetNode.Variable ->
            Pair(
                PythonVariableReferenceNode(
                    name = context.name(shedTarget),
                    source = source
                ),
                listOf()
            )
        is TargetNode.Tuple -> {
            val (tupleMembers, statements) = shedTarget.elements.map { element ->
                generateTargetCode(element, context)
            }.unzip()
            Pair(
                PythonTupleNode(
                    members = tupleMembers,
                    source = source
                ),
                statements.flatten()
            )
        }
        is TargetNode.Fields -> {
            val temporaryName = context.freshName("target")
            val temporaryReference = PythonVariableReferenceNode(temporaryName, source = source)
            val statements = shedTarget.fields.flatMap { (fieldName, fieldTarget) ->
                generateTargetAssignment(
                    fieldTarget,
                    PythonAttributeAccessNode(
                        receiver = temporaryReference,
                        attributeName = pythoniseName(fieldName.identifier),
                        source = source
                    ),
                    source,
                    context
                )
            }

            Pair(temporaryReference, statements)
        }
    }
}

private fun generateStatementCodeForExpression(
    expression: ExpressionNode,
    context: CodeGenerationContext,
    returnValue: ReturnValue,
    source: Source
): List<PythonStatementNode> {
    if (expression is IfNode) {
        return generateIfCode(expression, context, returnValue = returnValue)
    } else if (expression is WhenNode) {
        return generateWhenCode(expression, context, returnValue = returnValue)
    } else if (expression is HandleNode) {
        return generateCodeForHandle(expression, context, returnValue = returnValue)
    } else {
        return generateExpressionCode(expression, context).toStatements { pythonExpression ->
            returnValue(pythonExpression, source)
        }
    }
}

internal data class GeneratedCode<out T>(
    val value: T,
    val statements: List<PythonStatementNode>,
    val spilled: Boolean
) {
    fun <R> pureMap(func: (T) -> R) = GeneratedCode(func(value), statements, spilled = spilled)
    fun <R> flatMap(func: (T) -> GeneratedCode<R>): GeneratedCode<R> {
        val result = func(value)
        return GeneratedCode(
            result.value,
            statements + result.statements,
            spilled = spilled || result.spilled
        )
    }

    fun toStatements(func: (T) -> List<PythonStatementNode>): List<PythonStatementNode> {
        return statements + func(value)
    }

    fun <R> ifEmpty(func: (T) -> R): R? {
        if (statements.isEmpty()) {
            return func(value)
        } else {
            return null
        }
    }

    fun pureExpression(): T {
        if (statements.isEmpty()) {
            return value
        } else {
            throw NotImplementedError()
        }
    }

    companion object {
        fun <T> pure(value: T) = GeneratedCode(value, statements = listOf(), spilled = false)

        fun <T> flatten(codes: List<GeneratedCode<T>>): GeneratedCode<List<T>> {
            val values = codes.map { code -> code.value }
            val statements = codes.flatMap { code -> code.statements }
            // TODO: we should check that spillage has occurred correctly.
            // e.g. if spillage probably isn't correct
            // We probably need three states: PureExpression, Spilled, Unspilled
            val spilled = codes.any { code -> code.spilled }
            return GeneratedCode(values, statements, spilled = spilled)
        }

        fun <T> flatten(
            codes: List<GeneratedCode<T>>,
            spill: (T) -> GeneratedCode<T>
        ): GeneratedCode<List<T>> {
            val spilled = codes.mapIndexed { index, code ->
                handleSpillage(code, codes.drop(index + 1), spill)
            }
            return flatten(spilled)
        }

        fun <T1, T2, R> pureMap(
            code1: GeneratedCode<T1>,
            code2: GeneratedCode<T2>,
            func: (T1, T2) -> R
        ): GeneratedCode<R> {
            return GeneratedCode(
                func(code1.value, code2.value),
                statements = code1.statements + code2.statements,
                spilled = code1.spilled || code2.spilled
            )
        }

        fun <T1, T2, T3, R> pureMap(
            code1: GeneratedCode<T1>,
            code2: GeneratedCode<T2>,
            code3: GeneratedCode<T3>,
            func: (T1, T2, T3) -> R
        ): GeneratedCode<R> {
            return GeneratedCode(
                func(code1.value, code2.value, code3.value),
                statements = code1.statements + code2.statements + code3.statements,
                spilled = code1.spilled || code2.spilled || code3.spilled
            )
        }
    }
}

private typealias GeneratedExpression = GeneratedCode<PythonExpressionNode>
private typealias GeneratedExpressions = GeneratedCode<List<PythonExpressionNode>>

internal fun generateExpressionCode(node: ExpressionNode, context: CodeGenerationContext): GeneratedExpression {
    return node.accept(object : ExpressionNode.Visitor<GeneratedExpression> {
        override fun visit(node: UnitLiteralNode): GeneratedExpression {
            return GeneratedExpression.pure(
                PythonNoneLiteralNode(NodeSource(node))
            )
        }

        override fun visit(node: BooleanLiteralNode): GeneratedExpression {
            return GeneratedExpression.pure(
                PythonBooleanLiteralNode(node.value, NodeSource(node))
            )
        }

        override fun visit(node: IntegerLiteralNode): GeneratedExpression {
            return GeneratedExpression.pure(
                PythonIntegerLiteralNode(node.value, NodeSource(node))
            )
        }

        override fun visit(node: StringLiteralNode): GeneratedExpression {
            return GeneratedExpression.pure(
                PythonStringLiteralNode(node.value, NodeSource(node))
            )
        }

        override fun visit(node: UnicodeScalarLiteralNode): GeneratedExpression {
            val value = Character.toChars(node.value).joinToString()
            return GeneratedExpression.pure(
                PythonStringLiteralNode(value, NodeSource(node))
            )
        }

        override fun visit(node: TupleNode): GeneratedExpression {
            return GeneratedCode.flatten(
                node.elements.map { element ->
                    generateExpressionCode(element, context)
                },
                { expression ->
                    spillExpression(expression, context, source = NodeSource(node))
                }
            ).pureMap { elements ->
                PythonTupleNode(elements, source = NodeSource(node))
            }
        }

        override fun visit(node: ReferenceNode): GeneratedExpression {
            val name = context.name(node)

            return GeneratedExpression.pure(
                PythonVariableReferenceNode(name, NodeSource(node))
            )
        }

        override fun visit(node: UnaryOperationNode): GeneratedExpression {
            return generateExpressionCode(node.operand, context).pureMap { operand ->
                PythonUnaryOperationNode(
                    operator = generateCode(node.operator),
                    operand = operand,
                    source = NodeSource(node)
                )
            }
        }

        override fun visit(node: BinaryOperationNode): GeneratedExpression {
            val unspilledLeftCode = generateExpressionCode(node.left, context)
            val rightCode = generateExpressionCode(node.right, context)
            val leftCode = handleSpillage(unspilledLeftCode, listOf(rightCode), { expression ->
                spillExpression(expression, context, source = NodeSource(node))
            })

            return GeneratedExpression.pureMap(
                leftCode,
                rightCode,
                { left, right ->
                    PythonBinaryOperationNode(
                        operator = generateCode(node.operator),
                        left = left,
                        right = right,
                        source = NodeSource(node)
                    )
                }
            )
        }

        override fun visit(node: IsNode): GeneratedExpression {
            return generateExpressionCode(node.expression, context).pureMap { expression ->
                val discriminator = context.inspector.discriminatorForIsExpression(node)
                generateTypeCondition(expression, discriminator, NodeSource(node))
            }
        }

        override fun visit(node: CallNode): GeneratedExpression {
            return generatedCallCode(node, isPartial = false)
        }

        override fun visit(node: PartialCallNode): GeneratedExpression {
            return generatedCallCode(node, isPartial = true)
        }

        override fun visit(node: StaticCallNode): GeneratedExpression {
            return generateExpressionCode(node.receiver, context)
        }

        private fun generatedCallCode(node: CallBaseNode, isPartial: Boolean): GeneratedExpression {
            val source = NodeSource(node)

            val unspilledReceiverCode = generateExpressionCode(node.receiver, context)
            val unspilledPositionalArgumentsCode = generatePositionalArguments(node)
            val namedArgumentsCode = generateNamedArguments(node)

            val positionalArgumentsCode = handleSpillage(
                unspilledPositionalArgumentsCode,
                listOf(namedArgumentsCode),
                spill = { expressions ->
                    spillExpressions(expressions, context, source = source)
                }
            )

            val receiverCode = handleSpillage(
                unspilledReceiverCode,
                listOf(positionalArgumentsCode, namedArgumentsCode),
                spill = { expression ->
                    spillExpression(expression, context, source = source)
                }
            )

            return GeneratedCode.pureMap(
                receiverCode,
                positionalArgumentsCode,
                namedArgumentsCode
            ) { receiver, positionalArguments, namedArguments ->
                if (context.inspector.typeOfExpression(node.receiver) == EmptyFunctionType) {
                    val shapeType = context.inspector.typeOfExpression(node) as ShapeType
                    PythonFunctionCallNode(
                        function = generateCode(node.staticArguments.single(), context),
                        arguments = listOf(),
                        keywordArguments = shapeType.allFields.values.map { field ->
                            pythoniseName(field.name) to PythonNoneLiteralNode(source = source)
                        },
                        source = source,
                    )
                } else {
                    val partialArguments = if (isPartial) {
                        // TODO: better handling of builtin
                        listOf(PythonVariableReferenceNode("_partial", source = source))
                    } else {
                        listOf()
                    }

                    val pythonPositionalArguments = partialArguments + receiver + positionalArguments

                    PythonFunctionCallNode(
                        pythonPositionalArguments.first(),
                        pythonPositionalArguments.drop(1),
                        namedArguments,
                        source = source
                    )
                }
            }
        }

        private fun generatePositionalArguments(node: CallBaseNode): GeneratedExpressions {
            val results = node.positionalArguments.map({ argument -> generateExpressionCode(argument, context) })
            return GeneratedExpressions.flatten(results, spill = { expression ->
                spillExpression(expression, context, source = NodeSource(node))
            })
        }

        private fun generateNamedArguments(node: CallBaseNode): GeneratedCode<List<Pair<String, PythonExpressionNode>>> {
            // TODO: handle splat
            val results = node.fieldArguments.map({ argument ->
                generateExpressionCode((argument as FieldArgumentNode.Named).expression, context).pureMap { expression ->
                    pythoniseName(argument.name) to expression
                }
            })
            return GeneratedCode.flatten(results, spill = { (name, expression) ->
                spillExpression(expression, context, source = NodeSource(node))
                    .pureMap { expression -> name to expression }
            })
        }

        override fun visit(node: FieldAccessNode): GeneratedExpression {
            return generateExpressionCode(node.receiver, context).pureMap { receiver ->
                PythonAttributeAccessNode(
                    receiver,
                    pythoniseName(node.fieldName.identifier),
                    source = NodeSource(node)
                )
            }
        }

        override fun visit(node: FunctionExpressionNode): GeneratedExpression {
            if (node.body.statements.isEmpty()) {
                return GeneratedExpression.pure(
                    PythonLambdaNode(
                        parameters = generateParameters(node, context),
                        body = PythonNoneLiteralNode(source = NodeSource(node)),
                        source = NodeSource(node)
                    )
                )
            }

            val statement = node.body.statements.singleOrNull()
            if (statement != null && statement is ExpressionStatementNode && statement.type == ExpressionStatementNode.Type.VALUE) {
                val result = generateExpressionCode(statement.expression, context)
                    .ifEmpty { expression -> PythonLambdaNode(
                        parameters = generateParameters(node, context),
                        body = expression,
                        source = NodeSource(node)
                    ) }
                if (result != null) {
                    return GeneratedExpression.pure(result)
                }
            }

            val auxiliaryFunction = generateFunction(
                name = context.freshName(),
                node = node,
                context = context
            )

            return GeneratedExpression(
                PythonVariableReferenceNode(auxiliaryFunction.name, source = node.source),
                statements = listOf(auxiliaryFunction),
                spilled = false
            )
        }

        override fun visit(node: IfNode): GeneratedExpression {
            return generateCodeForBranchingExpression(
                node,
                context,
                ::generateIfCode
            )
        }

        override fun visit(node: WhenNode): GeneratedExpression {
            return generateCodeForBranchingExpression(
                node,
                context,
                ::generateWhenCode
            )
        }

        override fun visit(node: HandleNode): GeneratedExpression {
            return generateCodeForBranchingExpression(
                node,
                context,
                ::generateCodeForHandle
            )
        }
    })
}

private fun generateIfCode(
    node: IfNode,
    context: CodeGenerationContext,
    returnValue: ReturnValue
): List<PythonStatementNode>{
    // TODO: handle spillage properly
    return GeneratedCode.flatten(node.conditionalBranches.map { branch ->
        generateExpressionCode(branch.condition, context).pureMap { condition ->
            PythonConditionalBranchNode(
                condition = condition,
                body = generateBlockCode(
                    branch.body,
                    context,
                    returnValue = returnValue
                ),
                source = NodeSource(branch)
            )
        }
    }).toStatements { conditionalBranches ->
        val elseBranch = generateBlockCode(
            node.elseBranch,
            context,
            returnValue = returnValue
        )

        listOf(
            PythonIfStatementNode(
                conditionalBranches = conditionalBranches,
                elseBranch = elseBranch,
                source = NodeSource(node)
            )
        )
    }
}

private fun generateWhenCode(
    node: WhenNode,
    context: CodeGenerationContext,
    returnValue: ReturnValue
): List<PythonStatementNode> {
    return generateExpressionCode(node.expression, context).toStatements { expression ->
        val (assignment, expressionName) = if (expression is PythonVariableReferenceNode) {
            Pair(null, expression.name)
        } else {
            val expressionName = context.freshName(expression = node.expression)
            val assignment = assign(expressionName, expression, NodeSource(node))
            Pair(assignment, expressionName)
        }

        val branches = node.conditionalBranches.map { branch ->
            val condition = generateTypeCondition(
                expression = PythonVariableReferenceNode(
                    name = expressionName,
                    source = NodeSource(branch)
                ),
                discriminator = context.inspector.discriminatorForWhenBranch(node, branch),
                source = NodeSource(branch)
            )

            val target = if (branch.target.fields.isEmpty()) {
                listOf()
            } else {
                generateTargetAssignment(
                    shedTarget = branch.target,
                    pythonExpression = PythonVariableReferenceNode(
                        name = expressionName,
                        source = NodeSource(branch)
                    ),
                    source = NodeSource(branch),
                    context = context,
                )
            }

            val body = generateBlockCode(
                branch.body,
                context,
                returnValue = returnValue
            )

            PythonConditionalBranchNode(
                condition = condition,
                body = target + body,
                source = NodeSource(branch)
            )
        }

        val elseBranch = generateBlockCode(node.elseBranch, context, returnValue = returnValue)

        assignment.nullableToList() + listOf(
            PythonIfStatementNode(
                conditionalBranches = branches,
                elseBranch = elseBranch,
                source = NodeSource(node)
            )
        )
    }
}

private fun assign(
    targetName: String,
    expression: PythonExpressionNode,
    source: Source
): PythonStatementNode {
    return PythonAssignmentNode(
        target = PythonVariableReferenceNode(
            name = targetName,
            source = source
        ),
        expression = expression,
        source = source
    )
}

private fun assignSelf(
    targetName: String,
    expression: PythonExpressionNode,
    source: Source
): PythonStatementNode {
    return PythonAssignmentNode(
        target = PythonAttributeAccessNode(
            receiver = PythonVariableReferenceNode("self", source = source),
            attributeName = targetName,
            source = source
        ),
        expression = expression,
        source = source
    )
}

private fun <T1, T2> handleSpillage(
    leftCode: GeneratedCode<T1>,
    rightCode: Collection<GeneratedCode<T2>>,
    spill: (T1) -> GeneratedCode<T1>
): GeneratedCode<T1> {
    return if (!leftCode.spilled && rightCode.any { code -> code.spilled }) {
        leftCode.flatMap { left -> spill(left) }
    } else {
        leftCode
    }
}

private fun spillExpressions(
    expressions: List<PythonExpressionNode>,
    context: CodeGenerationContext,
    source: Source
): GeneratedCode<List<PythonExpressionNode>> {
    return GeneratedCode.flatten(expressions.map { expression ->
        spillExpression(expression, context, source = source)
    })
}

private fun spillExpression(
    expression: PythonExpressionNode,
    context: CodeGenerationContext,
    source: Source
): GeneratedCode<PythonExpressionNode> {
    if (isScalar(expression)) {
        return GeneratedCode.pure(expression)
    } else {
        val name = context.freshName()
        val assignment = assign(name, expression, source = source)
        val reference = PythonVariableReferenceNode(name, source = source)
        return GeneratedCode(
            reference,
            statements = listOf(assignment),
            spilled = true
        )
    }
}

private fun isScalar(node: PythonExpressionNode): Boolean {
    return node.accept(object: PythonExpressionNode.Visitor<Boolean> {
        override fun visit(node: PythonNoneLiteralNode) = true
        override fun visit(node: PythonBooleanLiteralNode) = true
        override fun visit(node: PythonIntegerLiteralNode) = true
        override fun visit(node: PythonStringLiteralNode) = true
        override fun visit(node: PythonVariableReferenceNode) = true
        override fun visit(node: PythonTupleNode) = throw NotImplementedError()
        override fun visit(node: PythonUnaryOperationNode) = false
        override fun visit(node: PythonBinaryOperationNode) = false
        override fun visit(node: PythonConditionalOperationNode) = false
        override fun visit(node: PythonFunctionCallNode) = false
        override fun visit(node: PythonAttributeAccessNode) = false
        override fun visit(node: PythonLambdaNode) = true
    })
}

private fun generateTypeCondition(
    expression: PythonExpressionNode,
    discriminator: Discriminator,
    source: Source
): PythonExpressionNode {
    return PythonBinaryOperationNode(
        PythonBinaryOperator.EQUALS,
        PythonAttributeAccessNode(expression, tagValueAttributeName, source),
        PythonStringLiteralNode(discriminator.tagValue.value.value, source = source),
        source = source
    )
}

private fun generateCode(operator: UnaryOperator): PythonUnaryOperator {
    return when (operator) {
        UnaryOperator.MINUS -> PythonUnaryOperator.MINUS
        UnaryOperator.NOT -> PythonUnaryOperator.NOT
    }
}

private fun generateCode(operator: BinaryOperator): PythonBinaryOperator {
    return when (operator) {
        BinaryOperator.EQUALS -> PythonBinaryOperator.EQUALS
        BinaryOperator.NOT_EQUAL -> PythonBinaryOperator.NOT_EQUAL
        BinaryOperator.LESS_THAN -> PythonBinaryOperator.LESS_THAN
        BinaryOperator.LESS_THAN_OR_EQUAL -> PythonBinaryOperator.LESS_THAN_OR_EQUAL
        BinaryOperator.GREATER_THAN -> PythonBinaryOperator.GREATER_THAN
        BinaryOperator.GREATER_THAN_OR_EQUAL -> PythonBinaryOperator.GREATER_THAN_OR_EQUAL
        BinaryOperator.ADD -> PythonBinaryOperator.ADD
        BinaryOperator.SUBTRACT -> PythonBinaryOperator.SUBTRACT
        BinaryOperator.MULTIPLY -> PythonBinaryOperator.MULTIPLY
        BinaryOperator.DIVIDE -> throw NotImplementedError()
        BinaryOperator.AND -> PythonBinaryOperator.AND
        BinaryOperator.OR -> PythonBinaryOperator.OR
    }
}

internal fun generateCode(node: StaticExpressionNode, context: CodeGenerationContext): PythonExpressionNode {
    // TODO: test code gen for types
    return node.accept(object : StaticExpressionNode.Visitor<PythonExpressionNode> {
        override fun visit(node: ReferenceNode): PythonExpressionNode {
            // TODO: test renaming
            return generateCodeForReference(node, context)
        }

        override fun visit(node: StaticFieldAccessNode): PythonExpressionNode {
            return PythonAttributeAccessNode(
                generateCode(node.receiver, context),
                pythoniseName(node.fieldName.identifier),
                NodeSource(node)
            )
        }

        override fun visit(node: StaticApplicationNode): PythonExpressionNode {
            return generateCode(node.receiver, context)
        }

        override fun visit(node: FunctionTypeNode): PythonExpressionNode {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: TupleTypeNode): PythonExpressionNode {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: StaticUnionNode): PythonExpressionNode {
            throw UnsupportedOperationException("not implemented")
        }
    })
}

private fun generateCodeForReference(node: ReferenceNode, context: CodeGenerationContext): PythonVariableReferenceNode {
    return PythonVariableReferenceNode(context.name(node), NodeSource(node))
}

private const val tagValueAttributeName = "_tag_value"
