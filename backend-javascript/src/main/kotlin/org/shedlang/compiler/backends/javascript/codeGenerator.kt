package org.shedlang.compiler.backends.javascript

import org.shedlang.compiler.Module
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.CodeInspector
import org.shedlang.compiler.backends.FieldValue
import org.shedlang.compiler.backends.ModuleCodeInspector
import org.shedlang.compiler.backends.isConstant
import org.shedlang.compiler.backends.javascript.ast.*
import org.shedlang.compiler.types.*

internal fun generateCode(module: Module.Shed): JavascriptModuleNode {
    val context = CodeGenerationContext(
        inspector = ModuleCodeInspector(module),
        moduleName = module.name
    )

    val node = module.node
    val imports = node.imports.map({ importNode -> generateCode(module, importNode) })
    val body = node.body.flatMap { statement -> generateCode(statement, context)  }
    val exports = node.exports.map { export -> generateExport(export, NodeSource(module.node)) }

    val castImports = generateCastImports(module, context)

    return JavascriptModuleNode(
        imports + castImports + body + exports,
        source = NodeSource(node)
    )
}

internal class CodeGenerationContext(
    val inspector: CodeInspector,
    val moduleName: ModuleName,
    var hasCast: Boolean = false
) {
    private var hasAwaitStack: MutableList<Boolean> = mutableListOf()

    fun enterFunction() {
        hasAwaitStack.add(false)
    }

    fun markAwait() {
        hasAwaitStack[hasAwaitStack.lastIndex] = true
    }

    fun exitFunction(): FunctionContext {
        val hasAwait = hasAwaitStack.removeAt(hasAwaitStack.lastIndex)
        return FunctionContext(hasAwait = hasAwait)
    }
}

internal class FunctionContext(val hasAwait: Boolean)

private fun generateCode(module: Module.Shed, import: ImportNode): JavascriptStatementNode {
    val source = NodeSource(import)
    val expression = generateImportExpression(importPath = import.path, module = module, source = source)

    return JavascriptConstNode(
        target = generateCodeForTarget(import.target),
        expression = expression,
        source = source
    )
}

private fun generateCastImports(module: Module.Shed, context: CodeGenerationContext): List<JavascriptStatementNode> {
    val source = NodeSource(module.node)
    return if (context.hasCast) {
        val listOf = listOf(
            JavascriptConstNode(
                target = JavascriptObjectDestructuringNode(
                    properties = listOf(
                        "none" to JavascriptVariableReferenceNode("\$none", source = source),
                        "some" to JavascriptVariableReferenceNode("\$some", source = source)
                    ),
                    source = source
                ),
                expression = generateImportExpression(
                    importPath = ImportPath.absolute(listOf("Core", "Options")),
                    module = module,
                    source = source
                ),
                source = source
            )
        )
        listOf
    } else {
        listOf()
    }
}

private fun generateImportExpression(importPath: ImportPath, module: Module.Shed, source: NodeSource): JavascriptFunctionCallNode {
    val importBase = when (importPath.base) {
        ImportPathBase.Relative -> "./"
        ImportPathBase.Absolute -> "./" + "../".repeat(module.name.size - 1)
    }

    val javascriptImportPath = importBase + importPath.parts.map(Identifier::value).joinToString("/")

    return JavascriptFunctionCallNode(
        JavascriptVariableReferenceNode("require", source = source),
        listOf(JavascriptStringLiteralNode(javascriptImportPath, source = source)),
        source = source
    )
}

private fun generateExport(export: ReferenceNode, source: Source): JavascriptExpressionStatementNode {
    val name = export.name
    return JavascriptExpressionStatementNode(
        JavascriptAssignmentNode(
            JavascriptPropertyAccessNode(
                JavascriptVariableReferenceNode("exports", source = source),
                generateName(name),
                source = source
            ),
            JavascriptVariableReferenceNode(generateName(name), source = source),
            source = source
        ),
        source = source
    )
}

internal fun generateCode(node: ModuleStatementNode, context: CodeGenerationContext): List<JavascriptStatementNode> {
    return node.accept(object : ModuleStatementNode.Visitor<List<JavascriptStatementNode>> {
        override fun visit(node: TypeAliasNode): List<JavascriptStatementNode> = listOf()
        override fun visit(node: ShapeNode): List<JavascriptStatementNode> = listOf(generateCodeForShape(node, context))
        override fun visit(node: UnionNode): List<JavascriptStatementNode> = generateCodeForUnion(node, context)
        override fun visit(node: FunctionDeclarationNode): List<JavascriptStatementNode> = generateCodeForFunctionDeclaration(node, context)
        override fun visit(node: ValNode): List<JavascriptStatementNode> = listOf(generateCode(node, context))
        override fun visit(node: VarargsDeclarationNode) = listOf(generateCodeForVarargsDeclaration(node, context))
    })
}

private fun generateCodeForShape(node: ShapeBaseNode, context: CodeGenerationContext) : JavascriptStatementNode {
    val source = NodeSource(node)

    val tagValue = context.inspector.shapeTagValue(node)
    val tagValueArgument = if (tagValue == null) {
        JavascriptNullLiteralNode(source = node.source)
    } else {
        JavascriptStringLiteralNode(tagValue.value.value, source = node.source)
    }

    return javascriptConst(
        name = generateName(node.name),
        expression = JavascriptFunctionCallNode(
            JavascriptVariableReferenceNode(
                "\$shed.declareShape",
                source = source
            ),
            listOf(
                JavascriptStringLiteralNode(generateName(node.name), source = source),
                tagValueArgument,
                JavascriptArrayLiteralNode(
                    context.inspector.shapeFields(node).map { field ->
                        val fieldSource = field.source
                        val fieldValue = field.value
                        val value = when (fieldValue) {
                            null ->
                                // TODO: use undefined
                                JavascriptNullLiteralNode(source = fieldSource)

                            is FieldValue.Expression ->
                                generateCode(fieldValue.expression, context)

                            is FieldValue.Symbol ->
                                generateSymbolCode(fieldValue.symbol, source = fieldSource)
                        }

                        val jsFieldName = generateName(field.name)
                        JavascriptObjectLiteralNode(
                            properties = mapOf(
                                "get" to JavascriptFunctionExpressionNode(
                                    parameters = listOf("value"),
                                    body = listOf(
                                        JavascriptReturnNode(
                                            expression = JavascriptPropertyAccessNode(
                                                receiver = JavascriptVariableReferenceNode(
                                                    name = "value",
                                                    source = fieldSource
                                                ),
                                                propertyName = jsFieldName,
                                                source = fieldSource
                                            ),
                                            source = fieldSource
                                        )
                                    ),
                                    source = fieldSource
                                ),
                                "isConstant" to JavascriptBooleanLiteralNode(
                                    field.isConstant,
                                    source = fieldSource
                                ),
                                "jsName" to JavascriptStringLiteralNode(
                                    jsFieldName,
                                    source = fieldSource
                                ),
                                "name" to JavascriptStringLiteralNode(
                                    field.name.value,
                                    source = fieldSource
                                ),
                                "value" to value
                            ),
                            source = source
                        )
                    },
                    source = source
                )
            ),
            source = source
        ),
        source = source
    )
}

private fun generateCodeForUnion(node: UnionNode, context: CodeGenerationContext) : List<JavascriptStatementNode> {
    val source = NodeSource(node)
    return listOf(
        javascriptConst(
            name = generateName(node.name),
            expression = JavascriptNullLiteralNode(source = source),
            source = source
        )
    ) + node.members.map { member -> generateCodeForShape(member, context) }
}

private fun generateCodeForVarargsDeclaration(node: VarargsDeclarationNode, context: CodeGenerationContext): JavascriptStatementNode {
    val source = NodeSource(node)
    return javascriptConst(
        name = generateName(node.name),
        expression = JavascriptFunctionCallNode(
            function = JavascriptVariableReferenceNode("\$shed.varargs", source = source),
            arguments = listOf(
                generateCode(node.cons, context),
                generateCode(node.nil, context)
            ),
            source = source
        ),
        source = source
    )
}

private fun generateCodeForFunctionDeclaration(node: FunctionDeclarationNode, context: CodeGenerationContext): List<JavascriptStatementNode> {
    val javascriptFunction = generateFunction(node, context)

    val valueName = generateName(node.name)
    val functionName = if (javascriptFunction.isAsync) {
        valueName + "\$async"
    } else {
        valueName
    }

    val functionDeclaration = JavascriptFunctionDeclarationNode(
        name = functionName,
        isAsync = javascriptFunction.isAsync,
        parameters = javascriptFunction.parameters,
        body = javascriptFunction.body,
        source = NodeSource(node)
    )

    if (functionDeclaration.isAsync) {
        val constDeclaration = JavascriptConstNode(
            target = JavascriptVariableReferenceNode(valueName, source = NodeSource(node)),
            expression = JavascriptObjectLiteralNode(
                properties = mapOf("async" to JavascriptVariableReferenceNode(functionName, source = NodeSource(node))),
                source = NodeSource(node)
            ),
            source = NodeSource(node)
        )
        return listOf(functionDeclaration, constDeclaration)
    } else {
        return listOf(functionDeclaration)
    }
}

private fun generateFunction(node: FunctionNode, context: CodeGenerationContext): JavascriptFunctionNode {
    val positionalParameters = node.parameters.map(ParameterNode::name).map(Identifier::value)
    val namedParameterName = "\$named"
    val namedParameters = if (node.namedParameters.isEmpty()) {
        listOf()
    } else {
        listOf(namedParameterName)
    }
    val namedParameterAssignments = node.namedParameters.map { parameter ->
        javascriptConst(
            name = generateName(parameter.name),
            expression = JavascriptPropertyAccessNode(
                receiver = JavascriptVariableReferenceNode(
                    name = namedParameterName,
                    source = NodeSource(parameter)
                ),
                propertyName = generateName(parameter.name),
                source = NodeSource(parameter)
            ),
            source = NodeSource(parameter)
        )
    }

    context.enterFunction()
    val body = namedParameterAssignments + generateBlockCode(node.body, context)
    val bodyContext = context.exitFunction()

    return object: JavascriptFunctionNode {
        override val isAsync = bodyContext.hasAwait
        override val parameters = positionalParameters + namedParameters
        override val body = body
    }
}

private fun generateBlockCode(block: Block?, context: CodeGenerationContext): List<JavascriptStatementNode> {
    return if (block == null) {
        listOf()
    } else {
        block.statements.flatMap { statement -> generateCode(statement, context) }
    }
}

internal fun generateCode(node: FunctionStatementNode, context: CodeGenerationContext): List<JavascriptStatementNode> {
    return node.accept(object : FunctionStatementNode.Visitor<List<JavascriptStatementNode>> {
        override fun visit(node: ExpressionStatementNode): List<JavascriptStatementNode> {
            val expression = generateCode(node.expression, context)
            val source = NodeSource(node)
            return if (node.isReturn) {
                listOf(JavascriptReturnNode(expression, source))
            } else {
                listOf(JavascriptExpressionStatementNode(expression, source))
            }
        }

        override fun visit(node: ValNode): List<JavascriptStatementNode> {
            return listOf(generateCode(node, context))
        }

        override fun visit(node: FunctionDeclarationNode): List<JavascriptStatementNode> {
            return generateCodeForFunctionDeclaration(node, context)
        }
    })
}

private fun generateCode(node: ValNode, context: CodeGenerationContext): JavascriptConstNode {
    val source = NodeSource(node)
    val target = node.target
    val javascriptTarget = generateCodeForTarget(target)
    return JavascriptConstNode(
        target = javascriptTarget,
        expression = generateCode(node.expression, context),
        source = source
    )
}

private fun generateCodeForTarget(
    shedTarget: TargetNode
): JavascriptTargetNode {
    val source = NodeSource(shedTarget)
    return when (shedTarget) {
        is TargetNode.Variable -> {
            JavascriptVariableReferenceNode(generateName(shedTarget.name), source = source)
        }
        is TargetNode.Tuple -> JavascriptArrayDestructuringNode(
            elements = shedTarget.elements.map { targetElement ->
                generateCodeForTarget(targetElement)
            },
            source = source
        )
        is TargetNode.Fields -> JavascriptObjectDestructuringNode(
            properties = shedTarget.fields.map { (fieldName, fieldTarget) ->
                generateFieldName(fieldName) to generateCodeForTarget(fieldTarget)
            },
            source = source
        )
    }
}

internal fun generateCode(node: ExpressionNode, context: CodeGenerationContext): JavascriptExpressionNode {
    return node.accept(object : ExpressionNode.Visitor<JavascriptExpressionNode> {
        override fun visit(node: UnitLiteralNode): JavascriptExpressionNode {
            return JavascriptNullLiteralNode(NodeSource(node))
        }

        override fun visit(node: BooleanLiteralNode): JavascriptExpressionNode {
            return JavascriptBooleanLiteralNode(node.value, NodeSource(node))
        }

        override fun visit(node: IntegerLiteralNode): JavascriptExpressionNode {
            return JavascriptIntegerLiteralNode(node.value, NodeSource(node))
        }

        override fun visit(node: StringLiteralNode): JavascriptExpressionNode {
            return JavascriptStringLiteralNode(node.value, NodeSource(node))
        }

        override fun visit(node: UnicodeScalarLiteralNode): JavascriptExpressionNode {
            val value = Character.toChars(node.value).joinToString()
            return JavascriptStringLiteralNode(value, NodeSource(node))
        }

        override fun visit(node: SymbolNode): JavascriptExpressionNode {
            val symbol = Symbol(context.moduleName, node.name)
            return generateSymbolCode(symbol, NodeSource(node))
        }

        override fun visit(node: TupleNode): JavascriptExpressionNode {
            return JavascriptArrayLiteralNode(
                elements = node.elements.map { element -> generateCode(element, context) },
                source = NodeSource(node)
            )
        }

        override fun visit(node: ReferenceNode): JavascriptExpressionNode {
            return generateCodeForReferenceNode(node)
        }

        override fun visit(node: UnaryOperationNode): JavascriptExpressionNode {
            return JavascriptUnaryOperationNode(
                operator = generateCode(node.operator),
                operand = generateCode(node.operand, context),
                source = NodeSource(node)
            )
        }

        override fun visit(node: BinaryOperationNode): JavascriptExpressionNode {
            return JavascriptBinaryOperationNode(
                operator = generateCode(node.operator),
                left = generateCode(node.left, context),
                right = generateCode(node.right, context),
                source = NodeSource(node)
            )
        }

        override fun visit(node: IsNode): JavascriptExpressionNode {
            val discriminator = context.inspector.discriminatorForIsExpression(node)
            return generateTypeCondition(
                expression = generateCode(node.expression, context),
                discriminator = discriminator,
                source = NodeSource(node)
            )
        }

        override fun visit(node: CallNode): JavascriptExpressionNode {
            return generateCodeForCall(node, context)
        }

        override fun visit(node: PartialCallNode): JavascriptExpressionNode {
            val receiver = generateCode(node.receiver, context)
            val positionalArguments = node.positionalArguments.map { argument ->
                generateCode(argument, context)
            }
            val namedArguments = node.namedArguments
                .sortedBy { argument -> argument.name }
                .map { argument -> argument.name to generateCode(argument.expression, context) }

            val functionType = context.inspector.typeOfExpression(node.receiver) as FunctionType

            val partialArguments = listOf(receiver) +
                positionalArguments +
                namedArguments.map { argument -> argument.second }

            val partialParameters = listOf("\$func") +
                positionalArguments.indices.map { index -> "\$arg" + index } +
                namedArguments.map { argument -> generateName(argument.first) }

            val remainingPositionalParameters = (positionalArguments.size..functionType.positionalParameters.size - 1)
                .map { index -> "\$arg" + index }
            val remainingNamedParameters = functionType.namedParameters.keys - node.namedArguments.map { argument -> argument.name }
            val remainingParameters = remainingPositionalParameters + if (remainingNamedParameters.isEmpty()) {
                listOf()
            } else {
                listOf("\$named")
            }

            val finalNamedArgument = if (functionType.namedParameters.isEmpty()) {
                listOf()
            } else {
                listOf(JavascriptObjectLiteralNode(
                    properties = functionType.namedParameters
                        .keys
                        .sorted()
                        .associateBy(
                            { parameter -> generateName(parameter) },
                            { parameter ->
                                if (node.namedArguments.any { argument -> argument.name == parameter }) {
                                    JavascriptVariableReferenceNode(generateName(parameter), source = NodeSource(node))
                                } else {
                                    JavascriptPropertyAccessNode(
                                        receiver = JavascriptVariableReferenceNode(
                                            "\$named",
                                            source = NodeSource(node)
                                        ),
                                        propertyName = generateName(parameter),
                                        source = NodeSource(node)
                                    )
                                }
                            }
                        ),
                    source = NodeSource(node)
                ))
            }
            val fullArguments = functionType.positionalParameters.indices.map { index ->
                JavascriptVariableReferenceNode("\$arg" + index, source = NodeSource(node))
            } + finalNamedArgument

            return JavascriptFunctionCallNode(
                function = JavascriptFunctionExpressionNode(
                    parameters = partialParameters,
                    body = listOf(
                        JavascriptReturnNode(
                            expression = JavascriptFunctionExpressionNode(
                                parameters = remainingParameters,
                                body = listOf(
                                    JavascriptReturnNode(
                                        expression = JavascriptFunctionCallNode(
                                            function = JavascriptVariableReferenceNode(
                                                name = "\$func",
                                                source = NodeSource(node)
                                            ),
                                            arguments = fullArguments,
                                            source = NodeSource(node)
                                        ),
                                        source = NodeSource(node)
                                    )
                                ),
                                source = NodeSource(node)
                            ),
                            source = NodeSource(node)
                        )
                    ),
                    source = NodeSource(node)
                ),
                arguments = partialArguments,
                source = NodeSource(node)
            )
        }

        override fun visit(node: FieldAccessNode): JavascriptExpressionNode {
            return JavascriptPropertyAccessNode(
                generateCode(node.receiver, context),
                generateFieldName(node.fieldName),
                source = NodeSource(node)
            )
        }

        override fun visit(node: FunctionExpressionNode): JavascriptExpressionNode {
            val javascriptFunction = generateFunction(node, context)
            return JavascriptFunctionExpressionNode(
                isAsync = javascriptFunction.isAsync,
                parameters = javascriptFunction.parameters,
                body = javascriptFunction.body,
                source = node.source
            )
        }

        override fun visit(node: IfNode): JavascriptExpressionNode {
            val source = NodeSource(node)

            context.enterFunction()
            val body = listOf(
                JavascriptIfStatementNode(
                    conditionalBranches = node.conditionalBranches.map { branch ->
                        JavascriptConditionalBranchNode(
                            condition = generateCode(branch.condition, context),
                            body = generateBlockCode(branch.body, context),
                            source = NodeSource(branch)
                        )
                    },
                    elseBranch = generateBlockCode(node.elseBranch, context),
                    source = source
                )
            )
            val bodyContext = context.exitFunction()

            return immediatelyInvokedFunction(
                isAsync = bodyContext.hasAwait,
                body = body,
                source = source,
                context = context
            )
        }

        override fun visit(node: WhenNode): JavascriptExpressionNode {
            val source = NodeSource(node)
            val temporaryName = "\$shed_tmp"

            context.enterFunction()
            val branches = node.branches.map { branch ->
                val expression = NodeSource(branch)
                val discriminator = context.inspector.discriminatorForWhenBranch(node, branch)
                val condition = generateTypeCondition(
                    JavascriptVariableReferenceNode(
                        name = temporaryName,
                        source = expression
                    ),
                    discriminator,
                    NodeSource(branch)
                )
                JavascriptConditionalBranchNode(
                    condition = condition,
                    body = generateBlockCode(branch.body, context),
                    source = NodeSource(branch)
                )
            }

            val elseBranch = generateBlockCode(node.elseBranch, context)

            val body = listOf(
                javascriptConst(
                    name = temporaryName,
                    expression = generateCode(node.expression, context),
                    source = source
                ),
                JavascriptIfStatementNode(
                    conditionalBranches = branches,
                    elseBranch = elseBranch,
                    source = source
                )
            )

            val bodyContext = context.exitFunction()

            return immediatelyInvokedFunction(
                isAsync = bodyContext.hasAwait,
                body = body,
                source = source,
                context = context
            )
        }
    })
}

private fun generateCodeForCall(node: CallNode, context: CodeGenerationContext): JavascriptExpressionNode {
    val positionalArguments = node.positionalArguments.map { argument ->
        generateCode(argument, context)
    }
    val namedArguments = if (node.namedArguments.isEmpty()) {
        listOf()
    } else {
        listOf(JavascriptObjectLiteralNode(
            node.namedArguments.associate { argument ->
                generateName(argument.name) to generateCode(argument.expression, context)
            },
            source = NodeSource(node)
        ))
    }
    val arguments = positionalArguments + namedArguments

    if (context.inspector.isCast(node)) {
        return generateCodeForCast(node, context)
    } else {
        val receiverValue = generateCode(node.receiver, context)
        val isAsyncCall = isAsyncCall(node, context)
        val receiver = if (isAsyncCall) {
            JavascriptPropertyAccessNode(
                receiver = receiverValue,
                propertyName = "async",
                source = NodeSource(node)
            )
        } else {
            receiverValue
        }

        val jsCall = JavascriptFunctionCallNode(
            function = receiver,
            arguments = arguments,
            source = NodeSource(node)
        )
        if (isAsyncCall) {
            return await(operand = jsCall, context = context, source = NodeSource(node))
        } else {
            return jsCall
        }
    }
}

private fun isAsyncCall(node: CallNode, context: CodeGenerationContext): Boolean {
    val receiverType = context.inspector.typeOfExpression(node.receiver)
    return receiverType is FunctionType && isSubEffect(subEffect = IoEffect, superEffect = receiverType.effect)
}

private fun generateCodeForCast(node: CallNode, context: CodeGenerationContext): JavascriptFunctionExpressionNode {
    context.hasCast = true
    val parameterName = "value"
    val parameterReference = JavascriptVariableReferenceNode(
        name = parameterName,
        source = NodeSource(node)
    )
    val typeCondition = generateTypeCondition(
        expression = parameterReference,
        discriminator = context.inspector.discriminatorForCast(node),
        source = NodeSource(node)
    )
    return JavascriptFunctionExpressionNode(
        parameters = listOf(parameterName),
        body = listOf(
            JavascriptReturnNode(
                expression = JavascriptConditionalOperationNode(
                    condition = typeCondition,
                    trueExpression = JavascriptFunctionCallNode(
                        function = JavascriptVariableReferenceNode(
                            name = "\$some",
                            source = NodeSource(node)
                        ),
                        arguments = listOf(parameterReference),
                        source = NodeSource(node)
                    ),
                    falseExpression = JavascriptVariableReferenceNode(
                        name = "\$none",
                        source = NodeSource(node)
                    ),
                    source = NodeSource(node)
                ),
                source = NodeSource(node)
            )
        ),
        source = NodeSource(node)
    )
}

private fun await(
    operand: JavascriptExpressionNode,
    context: CodeGenerationContext,
    source: Source
): JavascriptExpressionNode {
    context.markAwait()
    return JavascriptUnaryOperationNode(
        operator = JavascriptUnaryOperator.AWAIT,
        operand = operand,
        source = source
    )
}

private fun generateTypeCondition(
    expression: JavascriptExpressionNode,
    discriminator: Discriminator,
    source: NodeSource
): JavascriptExpressionNode {
    return JavascriptBinaryOperationNode(
        JavascriptBinaryOperator.EQUALS,
        JavascriptPropertyAccessNode(
            expression,
            tagValuePropertyName,
            source
        ),
        JavascriptStringLiteralNode(discriminator.tagValue.value.value, source),
        source = source
    )
}

private fun immediatelyInvokedFunction(
    isAsync: Boolean,
    body: List<JavascriptStatementNode>,
    source: Source,
    context: CodeGenerationContext
): JavascriptExpressionNode {
    val function = JavascriptFunctionExpressionNode(
        isAsync = isAsync,
        parameters = listOf(),
        body = body,
        source = source
    )
    val call = JavascriptFunctionCallNode(
        function = function,
        arguments = listOf(),
        source = source
    )
    if (isAsync) {
        return await(operand = call, source = source, context = context)
    } else {
        return call
    }
}

private fun generateSymbolCode(symbol: Symbol, source: Source): JavascriptExpressionNode {
    return JavascriptStringLiteralNode(symbol.fullName, source = source)
}

private fun generateCode(operator: UnaryOperator): JavascriptUnaryOperator {
    return when (operator) {
        UnaryOperator.MINUS -> JavascriptUnaryOperator.MINUS
        UnaryOperator.NOT -> JavascriptUnaryOperator.NOT
    }
}

private fun generateCode(operator: BinaryOperator): JavascriptBinaryOperator {
    return when (operator) {
        BinaryOperator.EQUALS -> JavascriptBinaryOperator.EQUALS
        BinaryOperator.NOT_EQUAL -> JavascriptBinaryOperator.NOT_EQUAL
        BinaryOperator.LESS_THAN -> JavascriptBinaryOperator.LESS_THAN
        BinaryOperator.LESS_THAN_OR_EQUAL -> JavascriptBinaryOperator.LESS_THAN_OR_EQUAL
        BinaryOperator.GREATER_THAN -> JavascriptBinaryOperator.GREATER_THAN
        BinaryOperator.GREATER_THAN_OR_EQUAL -> JavascriptBinaryOperator.GREATER_THAN_OR_EQUAL
        BinaryOperator.ADD -> JavascriptBinaryOperator.ADD
        BinaryOperator.SUBTRACT -> JavascriptBinaryOperator.SUBTRACT
        BinaryOperator.MULTIPLY -> JavascriptBinaryOperator.MULTIPLY
        BinaryOperator.AND -> JavascriptBinaryOperator.AND
        BinaryOperator.OR -> JavascriptBinaryOperator.OR
    }
}

private fun generateCodeForReferenceNode(node: ReferenceNode): JavascriptExpressionNode {
    return JavascriptVariableReferenceNode(generateName(node.name), NodeSource(node))
}

private fun generateFieldName(fieldName: FieldNameNode): String {
    return generateName(fieldName.identifier)
}

private fun generateName(identifier: Identifier): String {
    // TODO: remove $ and . from identifiers
    return if (isJavascriptKeyword(identifier.value)) {
        identifier.value + "_"
    } else {
        identifier.value
    }.replace("$", "_").replace(".", "_")
}

private fun javascriptConst(
    name: String,
    expression: JavascriptExpressionNode,
    source: Source
): JavascriptConstNode {
    return JavascriptConstNode(
        target = JavascriptVariableReferenceNode(name, source = source),
        expression = expression,
        source = source
    )
}

val javascriptKeywords = setOf(
    "await",
    "break",
    "case",
    "catch",
    "class",
    "const",
    "continue",
    "debugger",
    "default",
    "delete",
    "do",
    "else",
    "enum",
    "export",
    "extends",
    "false",
    "finally",
    "for",
    "function",
    "if",
    "implements",
    "import",
    "in",
    "instanceof",
    "interface",
    "let",
    "new",
    "null",
    "package",
    "private",
    "protected",
    "public",
    "return",
    "static",
    "super",
    "switch",
    "this",
    "throw",
    "true",
    "try",
    "typeof",
    "var",
    "void",
    "while",
    "with",
    "yield"
)

fun isJavascriptKeyword(value: String): Boolean {
    return value in javascriptKeywords
}

private const val tagValuePropertyName = "\$tagValue"
