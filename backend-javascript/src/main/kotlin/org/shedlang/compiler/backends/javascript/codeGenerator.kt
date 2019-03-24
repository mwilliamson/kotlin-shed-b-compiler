package org.shedlang.compiler.backends.javascript

import org.shedlang.compiler.Module
import org.shedlang.compiler.Types
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.javascript.ast.*
import org.shedlang.compiler.findDiscriminator
import org.shedlang.compiler.types.*

internal fun generateCode(module: Module.Shed): JavascriptModuleNode {
    val context = CodeGenerationContext(moduleName = module.name, types = module.types)

    val node = module.node
    val imports = node.imports.map({ importNode -> generateCode(module, importNode) })
    val body = node.body.flatMap { statement -> generateCode(statement, context)  }
    val exports = node.exports.map { export -> generateExport(export, NodeSource(module.node)) }
    return JavascriptModuleNode(
        imports + body + exports,
        source = NodeSource(node)
    )
}

internal class CodeGenerationContext(val moduleName: List<Identifier>, private val types: Types) {
    fun typeOfExpression(node: ExpressionNode): Type {
        return types.typeOf(node)
    }

    fun findDiscriminator(node: IsNode) = findDiscriminator(node, types = types)
    fun findDiscriminator(node: WhenNode, branch: WhenBranchNode) = findDiscriminator(node, branch, types = types)
    fun shapeFields(node: ShapeBaseNode) = types.shapeFields(node)
}

private fun generateCode(module: Module.Shed, import: ImportNode): JavascriptStatementNode {
    val source = NodeSource(import)

    val importBase = when (import.path.base) {
        ImportPathBase.Relative -> "./"
        ImportPathBase.Absolute -> "./" + "../".repeat(module.name.size - 1)
    }

    val importPath = importBase + import.path.parts.map(Identifier::value).joinToString("/")

    return JavascriptConstNode(
        name = generateName(import.name),
        expression = JavascriptFunctionCallNode(
            JavascriptVariableReferenceNode("require", source = source),
            listOf(JavascriptStringLiteralNode(importPath, source = source)),
            source = source
        ),
        source = source
    )
}

private fun generateExport(name: Identifier, source: Source): JavascriptExpressionStatementNode {
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
        override fun visit(node: FunctionDeclarationNode): List<JavascriptStatementNode> = listOf(generateCodeForFunctionDeclaration(node, context))
        override fun visit(node: ValNode): List<JavascriptStatementNode> = listOf(generateCode(node, context))
    })
}

private fun generateCodeForShape(node: ShapeBaseNode, context: CodeGenerationContext) : JavascriptStatementNode {
    // TODO: remove duplication with InterpreterLoader and Python code generator

    val constantFields = context.shapeFields(node)
        .values
        .filter { field -> field.isConstant }

    val source = NodeSource(node)
    return JavascriptConstNode(
        name = generateName(node.name),
        expression = JavascriptFunctionCallNode(
            JavascriptVariableReferenceNode(
                "\$shed.declareShape",
                source = source
            ),
            listOf(
                JavascriptStringLiteralNode(generateName(node.name), source = source),
                JavascriptObjectLiteralNode(
                    constantFields.associateBy(
                        { field -> generateName(field.name) },
                        { field ->
                            val fieldValueNode = node.fields
                                .find { fieldNode -> fieldNode.name == field.name }
                                ?.value
                            val fieldType = field.type
                            if (fieldValueNode != null) {
                                generateCode(fieldValueNode, context)
                            } else if (fieldType is SymbolType) {
                                generateSymbolCode(fieldType.symbol, source = NodeSource(node))
                            } else {
                                // TODO: throw better exception
                                throw Exception("Could not find value for constant field")
                            }
                        }
                    ),
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
        JavascriptConstNode(
            name = generateName(node.name),
            expression = JavascriptNullLiteralNode(source = source),
            source = source
        )
    ) + node.members.map { member -> generateCodeForShape(member, context) }
}

private fun generateCodeForFunctionDeclaration(node: FunctionDeclarationNode, context: CodeGenerationContext): JavascriptFunctionDeclarationNode {
    val javascriptFunction = generateFunction(node, context)
    return JavascriptFunctionDeclarationNode(
        name = generateName(node.name),
        parameters = javascriptFunction.parameters,
        body = javascriptFunction.body,
        source = NodeSource(node)
    )
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
        JavascriptConstNode(
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
    val body = namedParameterAssignments + generateCode(node.body.statements, context)

    return object: JavascriptFunctionNode {
        override val parameters = positionalParameters + namedParameters
        override val body = body
    }
}

private fun generateCode(statements: List<FunctionStatementNode>, context: CodeGenerationContext): List<JavascriptStatementNode> {
    return statements.map { statement -> generateCode(statement, context) }
}

internal fun generateCode(node: FunctionStatementNode, context: CodeGenerationContext): JavascriptStatementNode {
    return node.accept(object : FunctionStatementNode.Visitor<JavascriptStatementNode> {
        override fun visit(node: ExpressionStatementNode): JavascriptStatementNode {
            val expression = generateCode(node.expression, context)
            val source = NodeSource(node)
            return if (node.isReturn) {
                JavascriptReturnNode(expression, source)
            } else {
                JavascriptExpressionStatementNode(expression, source)
            }
        }

        override fun visit(node: ValNode): JavascriptStatementNode {
            return generateCode(node, context)
        }

        override fun visit(node: FunctionDeclarationNode): JavascriptStatementNode {
            return generateCodeForFunctionDeclaration(node, context)
        }
    })
}

private fun generateCode(node: ValNode, context: CodeGenerationContext): JavascriptConstNode {
    return JavascriptConstNode(
        name = generateName(node.name),
        expression = generateCode(node.expression, context),
        source = NodeSource(node)
    )
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

        override fun visit(node: CodePointLiteralNode): JavascriptExpressionNode {
            val value = Character.toChars(node.value).joinToString()
            return JavascriptStringLiteralNode(value, NodeSource(node))
        }

        override fun visit(node: SymbolNode): JavascriptExpressionNode {
            val symbol = Symbol(context.moduleName, node.name)
            return generateSymbolCode(symbol, NodeSource(node))
        }

        override fun visit(node: VariableReferenceNode): JavascriptExpressionNode {
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
            val discriminator = context.findDiscriminator(node)
            return generateTypeCondition(
                expression = generateCode(node.expression, context),
                discriminator = discriminator,
                source = NodeSource(node)
            )
        }

        override fun visit(node: CallNode): JavascriptExpressionNode {
            val positionalArguments = node.positionalArguments.map { argument ->
                generateCode(argument, context)
            }
            val namedArguments = if (node.namedArguments.isEmpty()) {
                listOf()
            } else {
                listOf(JavascriptObjectLiteralNode(
                    node.namedArguments.associate({ argument ->
                        generateName(argument.name) to generateCode(argument.expression, context)
                    }),
                    source = NodeSource(node)
                ))
            }
            val arguments = positionalArguments + namedArguments
            return JavascriptFunctionCallNode(
                generateCode(node.receiver, context),
                arguments,
                source = NodeSource(node)
            )
        }

        override fun visit(node: PartialCallNode): JavascriptExpressionNode {
            val receiver = generateCode(node.receiver, context)
            val positionalArguments = node.positionalArguments.map { argument ->
                generateCode(argument, context)
            }
            val namedArguments = node.namedArguments
                .sortedBy { argument -> argument.name }
                .map { argument -> argument.name to generateCode(argument.expression, context) }

            val functionType = context.typeOfExpression(node.receiver) as FunctionType

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
                generateName(node.fieldName.identifier),
                source = NodeSource(node)
            )
        }

        override fun visit(node: FunctionExpressionNode): JavascriptExpressionNode {
            val javascriptFunction = generateFunction(node, context)
            return JavascriptFunctionExpressionNode(
                parameters = javascriptFunction.parameters,
                body = javascriptFunction.body,
                source = node.source
            )
        }

        override fun visit(node: IfNode): JavascriptExpressionNode {
            val source = NodeSource(node)

            val body = listOf(
                JavascriptIfStatementNode(
                    conditionalBranches = node.conditionalBranches.map { branch ->
                        JavascriptConditionalBranchNode(
                            condition = generateCode(branch.condition, context),
                            body = generateCode(branch.body, context),
                            source = NodeSource(branch)
                        )
                    },
                    elseBranch = generateCode(node.elseBranch, context),
                    source = source
                )
            )

            return immediatelyInvokedFunction(body = body, source = source)
        }

        override fun visit(node: WhenNode): JavascriptExpressionNode {
            val source = NodeSource(node)
            val temporaryName = "\$shed_tmp"

            val branches = node.branches.map { branch ->
                val expression = NodeSource(branch)
                val discriminator = context.findDiscriminator(node, branch)
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
                    body = generateCode(branch.body, context),
                    source = NodeSource(branch)
                )
            }

            val elseBranch = generateCode(node.elseBranch.orEmpty(), context)

            return immediatelyInvokedFunction(
                body = listOf(
                    JavascriptConstNode(
                        name = temporaryName,
                        expression = generateCode(node.expression, context),
                        source = source
                    ),
                    JavascriptIfStatementNode(
                        conditionalBranches = branches,
                        elseBranch = elseBranch,
                        source = source
                    )
                ),
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
                    generateName(discriminator.fieldName),
                    source
                ),
                generateSymbolCode(discriminator.symbolType.symbol, source),
                source = source
            )
        }
    })
}

private fun immediatelyInvokedFunction(
    body: List<JavascriptStatementNode>,
    source: Source
): JavascriptExpressionNode {
    val function = JavascriptFunctionExpressionNode(
        parameters = listOf(),
        body = body,
        source = source
    )
    return JavascriptFunctionCallNode(
        function = function,
        arguments = listOf(),
        source = source
    )
}

private fun generateSymbolCode(symbol: Symbol, source: NodeSource): JavascriptExpressionNode {
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

private fun generateName(identifier: Identifier): String {
    // TODO: remove $ and . from identifiers
    return if (isJavascriptKeyword(identifier.value)) {
        identifier.value + "_"
    } else {
        identifier.value
    }.replace("$", "_").replace(".", "_")
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
