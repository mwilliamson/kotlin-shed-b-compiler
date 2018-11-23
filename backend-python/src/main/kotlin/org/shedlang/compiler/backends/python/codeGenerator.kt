package org.shedlang.compiler.backends.python

import org.shedlang.compiler.ResolvedReferences
import org.shedlang.compiler.Types
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.python.ast.*
import org.shedlang.compiler.findDiscriminator
import org.shedlang.compiler.types.Discriminator
import org.shedlang.compiler.types.Symbol
import org.shedlang.compiler.types.SymbolType
import java.lang.Exception

// TODO: check that builtins aren't renamed
// TODO: check imports aren't renamed

internal fun generateCode(
    node: ModuleNode,
    moduleName: List<Identifier>,
    references: ResolvedReferences,
    types: Types
): PythonModuleNode {
    return generateCode(node, CodeGenerationContext(moduleName, references, types))
}

internal class CodeGenerationContext(
    val moduleName: List<Identifier>,
    val references: ResolvedReferences,
    val types: Types,
    private val nodeNames: MutableMap<Int, String> = mutableMapOf(),
    private val namesInScope: MutableSet<String> = mutableSetOf()
) {
    fun enterScope(): CodeGenerationContext {
        return CodeGenerationContext(
            moduleName = moduleName,
            references = references,
            types = types,
            nodeNames = nodeNames,
            namesInScope = namesInScope.toMutableSet()
        )
    }

    fun freshName(name: String = "anonymous"): String {
        return generateName(name)
    }

    fun name(node: VariableBindingNode): String {
        return name(node.nodeId, node.name)
    }

    fun name(node: ReferenceNode): String {
        return name(references[node])
    }

    private fun name(nodeId: Int, name: Identifier): String {
        if (!nodeNames.containsKey(nodeId)) {
            val pythonName = generateName(name)
            nodeNames[nodeId] = pythonName
        }
        return nodeNames[nodeId]!!
    }

    private fun generateName(originalName: Identifier): String {
        return generateName(pythoniseName(originalName))
    }

    private fun generateName(originalName: String): String {
        var name = uniquifyName(originalName)
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
    val imports = node.imports.map({ import -> generateCode(import, context) })
    val body = node.body.flatMap({ child -> generateCode(child, context) })
    return PythonModuleNode(
        imports + body,
        source = NodeSource(node)
    )
}

private fun generateCode(node: ImportNode, context: CodeGenerationContext): PythonStatementNode {
    // TODO: assign names properly using context
    val source = NodeSource(node)

    val pythonPackageName = node.path.parts.take(node.path.parts.size - 1).map { part -> part.value }
    val module = when (node.path.base) {
        ImportPathBase.Relative -> "." + pythonPackageName.joinToString(".")
        ImportPathBase.Absolute -> (listOf(topLevelPythonPackageName) + pythonPackageName).joinToString(".")
    }

    val name = node.path.parts.last()

    return PythonImportFromNode(
        module = module,
        names = listOf(name.value to pythoniseName(name)),
        source = source
    )
}

internal fun generateCode(node: ModuleStatementNode, context: CodeGenerationContext): List<PythonStatementNode> {
    return node.accept(object : ModuleStatementNode.Visitor<List<PythonStatementNode>> {
        override fun visit(node: ShapeNode) = listOf(generateCode(node, context))
        override fun visit(node: UnionNode): List<PythonStatementNode> = listOf()
        override fun visit(node: FunctionDeclarationNode) = listOf(generateCode(node, context))
        override fun visit(node: ValNode) = generateCode(node, context)
    })
}

private fun generateCode(node: ShapeNode, context: CodeGenerationContext): PythonClassNode {
    // TODO: remove duplication with InterpreterLoader

    val (constantFields, variableFields) = context.types.shapeFields(node)
        .values
        .partition { field -> field.isConstant }

    val init = PythonFunctionNode(
        name = "__init__",
        parameters = listOf("self") + variableFields.map({ field -> pythoniseName(field.name) }),
        body = variableFields.map({ field ->
            PythonAssignmentNode(
                target = PythonAttributeAccessNode(
                    receiver = PythonVariableReferenceNode("self", source = NodeSource(node)),
                    attributeName = pythoniseName(field.name),
                    source = NodeSource(node)
                ),
                expression = PythonVariableReferenceNode(pythoniseName(field.name), source = NodeSource(node)),
                source = NodeSource(node)
            )
        }),
        source = NodeSource(node)
    )
    val body = constantFields.map { field ->
        val fieldValueNode = node.fields
            .find { fieldNode -> fieldNode.name == field.name }
            ?.value
        val fieldType = field.type
        val value = if (fieldValueNode != null) {
            generateExpressionCode(fieldValueNode, context).pureExpression()
        } else if (fieldType is SymbolType) {
            symbol(fieldType.symbol, source = NodeSource(node))
        } else {
            // TODO: throw better exception
            throw Exception("Could not find value for constant field")
        }
        PythonAssignmentNode(
            PythonVariableReferenceNode(pythoniseName(field.name), source = NodeSource(node)),
            value,
            source = NodeSource(node)
        )
    } + listOf(init)
    return PythonClassNode(
        // TODO: test renaming
        name = context.name(node),
        body = body,
        source = NodeSource(node)
    )
}

private fun generateCode(node: FunctionDeclarationNode, context: CodeGenerationContext): PythonFunctionNode {
    return generateFunction(context.name(node), node, context)
}

typealias ReturnValue = (ExpressionNode, Source) -> List<PythonStatementNode>

private fun generateFunction(name: String, node: FunctionNode, context: CodeGenerationContext): PythonFunctionNode {
    val bodyContext = context.enterScope()
    val parameters = generateParameters(node, bodyContext)

    var isTailRecursive = false
    val hasFunctionExpressions = hasFunctionExpressions(node)

    fun returnValue(expression: ExpressionNode, source: Source): List<PythonStatementNode> {
        val arguments = if (hasFunctionExpressions) {
            null
        } else {
            findTailRecursionArguments(node, expression, context)
        }
        if (arguments == null) {
            return generateExpressionCode(expression, bodyContext).toStatements { pythonExpression ->
                listOf(
                    PythonReturnNode(
                        expression = pythonExpression,
                        source = source
                    )
                )
            }
        } else {
            isTailRecursive = true
            return reassignArguments(arguments, NodeSource(expression), bodyContext)
        }
    }

    val bodyStatements = generateBlockCode(
        node.body.statements,
        bodyContext,
        returnValue = ::returnValue
    )
    val body = if (isTailRecursive) {
        listOf(
            PythonWhileNode(
                PythonBooleanLiteralNode(true, source = NodeSource(node)),
                bodyStatements,
                source = NodeSource(node)
            )
        )
    } else {
        bodyStatements
    }

    return PythonFunctionNode(
        // TODO: test renaming
        name = name,
        // TODO: test renaming
        parameters = parameters,
        body = body,
        source = NodeSource(node)
    )
}

private fun hasFunctionExpressions(function: FunctionNode): Boolean {
    return function.descendants().any { descendant -> descendant is FunctionExpressionNode }
}

private class TailRecursionArgument(val parameter: ParameterNode, val expression: ExpressionNode) {
    val name: Identifier
        get() = parameter.name
}

private fun findTailRecursionArguments(
    function: FunctionNode,
    expression: ExpressionNode,
    context: CodeGenerationContext
): List<TailRecursionArgument>? {
    if (expression is CallNode) {
        val receiver = expression.receiver
        if (receiver is VariableReferenceNode && context.references[receiver].nodeId == function.nodeId) {
            return findTailRecursionArguments(function, expression, context)
        } else {
            return null
        }
    } else {
        return null
    }
}

private fun findTailRecursionArguments(
    function: FunctionNode,
    expression: CallNode,
    context: CodeGenerationContext
): List<TailRecursionArgument> {
    return (function.parameters.zip(expression.positionalArguments, { parameter, argument ->
        TailRecursionArgument(
            parameter = parameter,
            expression = argument
        )
    }) + function.namedParameters.map { parameter ->
        TailRecursionArgument(
            parameter = parameter,
            expression = expression.namedArguments.find { argument -> argument.name == parameter.name }!!.expression
        )
    }).filterNot { argument ->
        val expression = argument.expression
        expression is VariableReferenceNode && context.references[expression].nodeId == argument.parameter.nodeId
    }
}

private fun reassignArguments(arguments: List<TailRecursionArgument>, source: NodeSource, context: CodeGenerationContext): List<PythonStatementNode> {
    val reassignments = arguments.map { argument ->
        val temporaryName = context.freshName(pythoniseName(argument.name))
        val newValue = generateExpressionCode(argument.expression, context).toStatements { pythonArgument ->
            listOf(assign(temporaryName, pythonArgument, source = source))
        }
        val temporaryReference = PythonVariableReferenceNode(
            temporaryName,
            source = source
        )
        val assignNewValue = assign(context.name(argument.parameter), temporaryReference, source = source)
        Pair(newValue, listOf(assignNewValue))
    }
    return reassignments.flatMap{ (first, _) -> first } + reassignments.flatMap { (_, second) -> second }
}

private fun generateParameters(function: FunctionNode, context: CodeGenerationContext) =
    function.parameters.map({ parameter -> context.name(parameter) }) +
        function.namedParameters.map({ parameter -> context.name(parameter) })

private fun generateBlockCode(
    statements: List<StatementNode>,
    context: CodeGenerationContext,
    returnValue: ReturnValue
): List<PythonStatementNode> {
    return statements.flatMap { statement ->
        generateStatementCode(statement, context, returnValue = returnValue)
    }
}

internal fun generateStatementCode(
    node: StatementNode,
    context: CodeGenerationContext,
    returnValue: ReturnValue
): List<PythonStatementNode> {
    return node.accept(object : StatementNode.Visitor<List<PythonStatementNode>> {
        override fun visit(node: ExpressionStatementNode): List<PythonStatementNode> {
            return generateCode(node, context, returnValue = returnValue)
        }

        override fun visit(node: ValNode): List<PythonStatementNode> {
            return generateCode(node, context)
        }
    })
}

private fun generateCode(
    node: ExpressionStatementNode,
    context: CodeGenerationContext,
    returnValue: ReturnValue
): List<PythonStatementNode> {
    fun expressionReturnValue(expression: ExpressionNode, source: Source): List<PythonStatementNode> {
        if (node.isReturn) {
            return returnValue(expression, source)
        } else {
            return generateExpressionCode(expression, context).toStatements { pythonExpression ->
                listOf(
                    PythonExpressionStatementNode(pythonExpression, source)
                )
            }
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
    fun expressionReturnValue(expression: ExpressionNode, source: Source): List<PythonStatementNode> {
        return generateExpressionCode(expression, context).toStatements { pythonExpression ->
            listOf(assign(context.name(node), pythonExpression, source = source))
        }
    }

    return generateStatementCodeForExpression(
        node.expression,
        context,
        returnValue = ::expressionReturnValue,
        source = NodeSource(node)
    )
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
    } else {
        return returnValue(expression, source)
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

        override fun visit(node: CodePointLiteralNode): GeneratedExpression {
            val value = Character.toChars(node.value).joinToString()
            return GeneratedExpression.pure(
                PythonStringLiteralNode(value, NodeSource(node))
            )
        }

        override fun visit(node: SymbolNode): GeneratedExpression {
            val source = NodeSource(node)
            val symbol = Symbol(context.moduleName, node.name)
            return GeneratedExpression.pure(symbol(symbol, source))
        }

        override fun visit(node: VariableReferenceNode): GeneratedExpression {
            val referent = context.references[node]
            val name = if (isBuiltin(referent, "intToString")) {
                "str"
            } else {
                context.name(node)
            }

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
                val discriminator = findDiscriminator(node, types = context.types)
                generateTypeCondition(expression, discriminator, NodeSource(node))
            }
        }

        override fun visit(node: CallNode): GeneratedExpression {
            return generatedCallCode(node, isPartial = false)
        }

        override fun visit(node: PartialCallNode): GeneratedExpression {
            return generatedCallCode(node, isPartial = true)
        }

        private fun generatedCallCode(node: CallBaseNode, isPartial: Boolean): GeneratedExpression {
            val unspilledReceiverCode = generateExpressionCode(node.receiver, context)
            val unspilledPositionalArgumentsCode = generatePositionalArguments(node)
            val namedArgumentsCode = generateNamedArguments(node)

            val positionalArgumentsCode = handleSpillage(
                unspilledPositionalArgumentsCode,
                listOf(namedArgumentsCode),
                spill = { expressions ->
                    spillExpressions(expressions, context, source = NodeSource(node))
                }
            )

            val receiverCode = handleSpillage(
                unspilledReceiverCode,
                listOf(positionalArgumentsCode, namedArgumentsCode),
                spill = { expression ->
                    spillExpression(expression, context, source = NodeSource(node))
                }
            )

            return GeneratedCode.pureMap(
                receiverCode,
                positionalArgumentsCode,
                namedArgumentsCode,
                { receiver, positionalArguments, namedArguments ->
                    val partialArguments = if (isPartial) {
                        // TODO: better handling of builtin
                        listOf(PythonVariableReferenceNode("_partial", source = NodeSource(node)))
                    } else {
                        listOf()
                    }

                    val pythonPositionalArguments = partialArguments + receiver + positionalArguments

                    PythonFunctionCallNode(
                        pythonPositionalArguments.first(),
                        pythonPositionalArguments.drop(1),
                        namedArguments,
                        source = NodeSource(node)
                    )
                }
            )
        }

        private fun generatePositionalArguments(node: CallBaseNode): GeneratedExpressions {
            val results = node.positionalArguments.map({ argument -> generateExpressionCode(argument, context) })
            return GeneratedExpressions.flatten(results, spill = { expression ->
                spillExpression(expression, context, source = NodeSource(node))
            })
        }

        private fun generateNamedArguments(node: CallBaseNode): GeneratedCode<List<Pair<String, PythonExpressionNode>>> {
            val results = node.namedArguments.map({ argument ->
                generateExpressionCode(argument.expression, context).pureMap { expression ->
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
            if (statement != null && statement is ExpressionStatementNode) {
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
            val targetName = context.freshName()

            val statements = generateIfCode(
                node,
                context,
                returnValue = { expression, source ->
                    generateExpressionCode(expression, context).toStatements { pythonExpression ->
                        listOf(assign(targetName, pythonExpression, source = source))
                    }
                }
            )

            val reference = PythonVariableReferenceNode(targetName, source = NodeSource(node))

            return GeneratedExpression(
                reference,
                statements = statements,
                spilled = true
            )
        }

        override fun visit(node: WhenNode): GeneratedExpression {
            val targetName = context.freshName()

            val statements = generateWhenCode(
                node,
                context,
                returnValue = { expression, source ->
                    generateExpressionCode(expression, context).toStatements { pythonExpression ->
                        listOf(assign(targetName, pythonExpression, source = source))
                    }
                }
            )

            val reference = PythonVariableReferenceNode(targetName, source = NodeSource(node))

            return GeneratedExpression(
                reference,
                statements = statements,
                spilled = true
            )
        }
    })
}

private fun generateIfCode(
    node: IfNode,
    context: CodeGenerationContext,
    returnValue: ReturnValue
): List<PythonStatementNode>{
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
    val expressionName = context.freshName()
    val expressionCode = generateExpressionCode(node.expression, context)
    val branches = node.branches.map { branch ->
        PythonConditionalBranchNode(
            condition = generateTypeCondition(
                expression = PythonVariableReferenceNode(
                    name = expressionName,
                    source = NodeSource(branch)
                ),
                discriminator = findDiscriminator(node, branch, types = context.types),
                source = NodeSource(branch)
            ),
            body = generateBlockCode(
                branch.body,
                context,
                returnValue = returnValue
            ),
            source = NodeSource(branch)
        )
    }

    return expressionCode.toStatements { expression ->
        listOf(
            assign(expressionName, expression, NodeSource(node)),
            PythonIfStatementNode(
                conditionalBranches = branches,
                elseBranch = listOf(),
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

private fun symbol(symbol: Symbol, source: Source): PythonExpressionNode {
    return PythonStringLiteralNode(symbol.fullName, source = source)
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
        PythonAttributeAccessNode(expression, pythoniseName(discriminator.fieldName), source),
        symbol(discriminator.symbolType.symbol, source = source),
        source = source
    )
}

private fun generateCode(operator: UnaryOperator): PythonUnaryOperator {
    return when (operator) {
        UnaryOperator.NOT -> PythonUnaryOperator.NOT
    }
}

private fun generateCode(operator: BinaryOperator): PythonBinaryOperator {
    return when (operator) {
        BinaryOperator.EQUALS -> PythonBinaryOperator.EQUALS
        BinaryOperator.LESS_THAN -> PythonBinaryOperator.LESS_THAN
        BinaryOperator.LESS_THAN_OR_EQUAL -> PythonBinaryOperator.LESS_THAN_OR_EQUAL
        BinaryOperator.GREATER_THAN -> PythonBinaryOperator.GREATER_THAN
        BinaryOperator.GREATER_THAN_OR_EQUAL -> PythonBinaryOperator.GREATER_THAN_OR_EQUAL
        BinaryOperator.ADD -> PythonBinaryOperator.ADD
        BinaryOperator.SUBTRACT -> PythonBinaryOperator.SUBTRACT
        BinaryOperator.MULTIPLY -> PythonBinaryOperator.MULTIPLY
        BinaryOperator.AND -> PythonBinaryOperator.AND
        BinaryOperator.OR -> PythonBinaryOperator.OR
    }
}

internal fun generateCode(node: StaticNode, context: CodeGenerationContext): PythonExpressionNode {
    // TODO: test code gen for types
    return node.accept(object : StaticNode.Visitor<PythonExpressionNode> {
        override fun visit(node: StaticReferenceNode): PythonExpressionNode {
            // TODO: test renaming
            return PythonVariableReferenceNode(context.name(node), NodeSource(node))
        }

        override fun visit(node: StaticFieldAccessNode): PythonExpressionNode {
            return PythonAttributeAccessNode(
                generateCode(node.receiver, context),
                pythoniseName(node.fieldName),
                NodeSource(node)
            )
        }

        override fun visit(node: StaticApplicationNode): PythonExpressionNode {
            return generateCode(node.receiver, context)
        }

        override fun visit(node: FunctionTypeNode): PythonExpressionNode {
            throw UnsupportedOperationException("not implemented")
        }
    })
}

private fun isBuiltin(referent: VariableBindingNode, name: String) =
    referent is BuiltinVariable && referent.name == Identifier(name)
