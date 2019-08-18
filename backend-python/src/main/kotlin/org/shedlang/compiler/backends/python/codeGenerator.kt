package org.shedlang.compiler.backends.python

import org.shedlang.compiler.Module
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.CodeInspector
import org.shedlang.compiler.backends.FieldValue
import org.shedlang.compiler.backends.ModuleCodeInspector
import org.shedlang.compiler.backends.isConstant
import org.shedlang.compiler.backends.python.ast.*
import org.shedlang.compiler.nullableToList
import org.shedlang.compiler.types.Discriminator
import org.shedlang.compiler.types.Symbol

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
        hasCast = HasCast(false)
    )
    return generateCode(module.node, context)
}

internal fun isPackage(moduleSet: ModuleSet, moduleName: List<Identifier>): Boolean {
    return moduleSet.modules.any { module ->
        module.name.size > moduleName.size && module.name.subList(0, moduleName.size) == moduleName
    }
}

internal data class HasCast(var value: Boolean)

internal class CodeGenerationContext(
    val inspector: CodeInspector,
    val moduleName: List<Identifier>,
    val isPackage: Boolean,
    private val nodeNames: MutableMap<Int, String> = mutableMapOf(),
    private val namesInScope: MutableSet<String> = mutableSetOf(),
    val hasCast: HasCast
) {
    fun enterScope(): CodeGenerationContext {
        return CodeGenerationContext(
            inspector = inspector,
            moduleName = moduleName,
            isPackage = isPackage,
            nodeNames = nodeNames,
            namesInScope = namesInScope.toMutableSet(),
            hasCast = hasCast
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
            val pythonName = generateName(name)
            nodeNames[nodeId] = pythonName
        }
        return nodeNames[nodeId]!!
    }

    private fun generateName(originalName: Identifier): String {
        return generateName(pythoniseName(originalName))
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
    val castImports = generateCastImports(node, context)
    return PythonModuleNode(
        imports + castImports + body,
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

private fun generateCastImports(node: ModuleNode, context: CodeGenerationContext): List<PythonStatementNode> {
    return if (context.hasCast.value) {
        listOf(
            PythonImportFromNode(
                module = listOf(topLevelPythonPackageName, "Stdlib", "Options").joinToString("."),
                names = listOf("none" to "_none", "some" to "_some"),
                source = NodeSource(node)
            )
        )
    } else {
        listOf()
    }
}

internal fun generateModuleStatementCode(node: ModuleStatementNode, context: CodeGenerationContext): List<PythonStatementNode> {
    return node.accept(object : ModuleStatementNode.Visitor<List<PythonStatementNode>> {
        override fun visit(node: TypeAliasNode): List<PythonStatementNode> = listOf()
        override fun visit(node: ShapeNode) = listOf(generateCodeForShape(node, context))
        override fun visit(node: UnionNode): List<PythonStatementNode> = generateCodeForUnion(node, context)
        override fun visit(node: FunctionDeclarationNode) = listOf(generateCodeForFunctionDeclaration(node, context))
        override fun visit(node: ValNode) = generateCode(node, context)
    })
}

private fun generateCodeForShape(node: ShapeBaseNode, context: CodeGenerationContext): PythonClassNode {
    // TODO: remove duplication with InterpreterLoader

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

    val fieldsClass = PythonClassNode(
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
                expression = PythonFunctionCallNode(
                    function = PythonVariableReferenceNode(
                        name = "_create_shape_field",
                        source = fieldSource
                    ),
                    arguments = listOf(),
                    keywordArguments = listOf(
                        "get" to PythonLambdaNode(
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
                        ),
                        "name" to PythonStringLiteralNode(
                            value = field.name.value,
                            source = fieldSource
                        )
                    ),
                    source = fieldSource
                ),
                source = fieldSource
            )
        },
        source = shapeSource
    )

    val body = shapeFields.mapNotNull { field ->
        val fieldValue = field.value

        val value = when (fieldValue) {
            null ->
                null

            is FieldValue.Expression ->
                generateExpressionCode(fieldValue.expression, context).pureExpression()

            is FieldValue.Symbol ->
                symbol(fieldValue.symbol, source = shapeSource)
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
    } + init + listOf(fieldsClass)
    return PythonClassNode(
        // TODO: test renaming
        name = context.name(node),
        body = body,
        source = shapeSource
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

private fun generateCodeForFunctionDeclaration(node: FunctionDeclarationNode, context: CodeGenerationContext): PythonFunctionNode {
    return generateFunction(context.name(node), node, context)
}

typealias ReturnValue = (ExpressionNode, Source) -> List<PythonStatementNode>

private fun generateFunction(name: String, node: FunctionNode, context: CodeGenerationContext): PythonFunctionNode {
    val bodyContext = context.enterScope()
    val parameters = generateParameters(node, bodyContext)
    // TODO: test variable capture in tail recursive functions
    var isTailRecursive = false
    val hasFunctionExpressions = hasFunctions(node)

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

private fun hasFunctions(function: FunctionNode): Boolean {
    return function.descendants().any { descendant -> descendant is FunctionNode }
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
        if (receiver is VariableReferenceNode && context.inspector.resolve(receiver).nodeId == function.nodeId) {
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
        expression is VariableReferenceNode && context.inspector.resolve(expression).nodeId == argument.parameter.nodeId
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
    statements: List<FunctionStatementNode>,
    context: CodeGenerationContext,
    returnValue: ReturnValue
): List<PythonStatementNode> {
    return statements.flatMap { statement ->
        generateCodeForFunctionStatement(statement, context, returnValue = returnValue)
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

        override fun visit(node: ValNode): List<PythonStatementNode> {
            return generateCode(node, context)
        }

        override fun visit(node: FunctionDeclarationNode): List<PythonStatementNode> {
            return listOf(generateCodeForFunctionDeclaration(node, context))
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
            generateTargetAssignment(node.target, pythonExpression, source, context)
        }
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

        override fun visit(node: VariableReferenceNode): GeneratedExpression {
            val referent = context.inspector.resolve(node)
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
                if (context.inspector.isCast(node)) {
                    context.hasCast.value = true

                    val parameterName = "value"
                    PythonLambdaNode(
                        parameters = listOf(parameterName),
                        body = PythonConditionalOperationNode(
                            condition = generateTypeCondition(
                                expression = PythonVariableReferenceNode(parameterName, source = source),
                                discriminator = context.inspector.discriminatorForCast(node),
                                source = source
                            ),
                            trueExpression = PythonFunctionCallNode(
                                function = PythonVariableReferenceNode("_some", source = source),
                                arguments = listOf(PythonVariableReferenceNode(parameterName, source = source)),
                                keywordArguments = listOf(),
                                source = source
                            ),
                            falseExpression = PythonVariableReferenceNode("_none", source = source),
                            source = source
                        ),
                        source = source
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

        val branches = node.branches.map { branch ->
            PythonConditionalBranchNode(
                condition = generateTypeCondition(
                    expression = PythonVariableReferenceNode(
                        name = expressionName,
                        source = NodeSource(branch)
                    ),
                    discriminator = context.inspector.discriminatorForWhenBranch(node, branch),
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

        val elseBranch = generateBlockCode(node.elseBranch.orEmpty(), context, returnValue = returnValue)

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
        PythonAttributeAccessNode(expression, pythoniseName(discriminator.fieldName), source),
        symbol(discriminator.symbolType.symbol, source = source),
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
        BinaryOperator.AND -> PythonBinaryOperator.AND
        BinaryOperator.OR -> PythonBinaryOperator.OR
    }
}

internal fun generateCode(node: StaticExpressionNode, context: CodeGenerationContext): PythonExpressionNode {
    // TODO: test code gen for types
    return node.accept(object : StaticExpressionNode.Visitor<PythonExpressionNode> {
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

        override fun visit(node: TupleTypeNode): PythonExpressionNode {
            throw UnsupportedOperationException("not implemented")
        }
    })
}

private fun isBuiltin(referent: VariableBindingNode, name: String) =
    referent is BuiltinVariable && referent.name == Identifier(name)
