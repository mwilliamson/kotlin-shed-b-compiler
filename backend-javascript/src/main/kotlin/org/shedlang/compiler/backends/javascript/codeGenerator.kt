package org.shedlang.compiler.backends.javascript

import org.shedlang.compiler.CompilerError
import org.shedlang.compiler.Module
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.*
import org.shedlang.compiler.backends.javascript.ast.*
import org.shedlang.compiler.types.*

internal fun generateCode(module: Module.Shed): JavascriptModuleNode {
    val context = CodeGenerationContext(
        inspector = ModuleCodeInspector(module),
        moduleName = module.name
    )

    val node = module.node
    val imports = node.imports.map { importNode -> generateCode(module, importNode, context) }
    val body = node.body.flatMap { statement -> generateCodeForModuleStatement(statement, context)  }
    val exports = node.exports.map { export -> generateExport(export, NodeSource(module.node)) }

    return JavascriptModuleNode(
        imports + body + exports,
        source = NodeSource(node)
    )
}

internal class CodeGenerationContext(
    val inspector: CodeInspector,
    val moduleName: ModuleName,
    var freshNameIndex: Int = 1
) {
    private var isAsyncStack: MutableList<Boolean> = mutableListOf()

    fun enterFunction(isAsync: Boolean) {
        isAsyncStack.add(isAsync)
    }

    fun exitFunction() {
        isAsyncStack.removeAt(isAsyncStack.lastIndex)
    }

    fun isAsync(): Boolean {
        return isAsyncStack.lastOrNull() ?: false
    }

    fun freshName(prefix: String): String {
        return prefix + "_" + (freshNameIndex++)
    }
}

private fun generateCode(module: Module.Shed, import: ImportNode, context: CodeGenerationContext): JavascriptStatementNode {
    val source = NodeSource(import)
    val expression = generateImportExpression(importPath = import.path, module = module, source = source)

    return JavascriptConstNode(
        target = generateCodeForTarget(import.target, context),
        expression = expression,
        source = source
    )
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

internal fun generateCodeForModuleStatement(node: ModuleStatementNode, context: CodeGenerationContext): List<JavascriptStatementNode> {
    return node.accept(object : ModuleStatementNode.Visitor<List<JavascriptStatementNode>> {
        override fun visit(node: EffectDefinitionNode): List<JavascriptStatementNode> = generateCodeForEffectDefinition(node)
        override fun visit(node: TypeAliasNode): List<JavascriptStatementNode> = listOf()
        override fun visit(node: ShapeNode): List<JavascriptStatementNode> = listOf(generateCodeForShape(node, context))
        override fun visit(node: UnionNode): List<JavascriptStatementNode> = generateCodeForUnion(node, context)
        override fun visit(node: FunctionDefinitionNode): List<JavascriptStatementNode> = generateCodeForFunctionDefinition(node, context)
        override fun visit(node: ValNode): List<JavascriptStatementNode> = listOf(generateCode(node, context))
        override fun visit(node: VarargsDeclarationNode) = listOf(generateCodeForVarargsDeclaration(node, context))
    })
}

private fun generateCodeForEffectDefinition(node: EffectDefinitionNode) : List<JavascriptStatementNode> {
    val source = NodeSource(node)

    val const = javascriptConst(
        name = generateName(node.name),
        expression = JavascriptFunctionCallNode(
            function = JavascriptVariableReferenceNode(
                "\$shed.defineEffect",
                source = source
            ),
            arguments = listOf(
                JavascriptArrayLiteralNode(
                    elements = node.operations.map { operation ->
                        JavascriptStringLiteralNode(generateName(operation.name), source = NodeSource(operation))
                    },
                    source = source
                )
            ),
            source = source
        ),
        source = source
    )

    return listOf(const)
}

private fun generateCodeForHandle(node: HandleNode, context: CodeGenerationContext): JavascriptExpressionNode {
    // TODO: sort out async effects
    val source = NodeSource(node)

    val handleReference = JavascriptVariableReferenceNode(
        "\$shed.handle",
        source = source
    )

    val arguments = listOf(
        JavascriptFunctionExpressionNode(
            parameters = listOf(),
            body = generateBlockCode(node.body, context),
            source = source
        ),
        JavascriptArrayLiteralNode(
            elements = node.handlers.map { handler ->
                JavascriptArrayLiteralNode(
                    elements = listOf(
                        JavascriptStringLiteralNode(generateName(handler.operationName), source = source),
                        generateCode(handler.function, context)
                    ),
                    source = source
                )
            },
            source = source
        )
    )

    if (context.isAsync()) {
        return await(
            JavascriptFunctionCallNode(
                function = JavascriptPropertyAccessNode(handleReference, "async", source = source),
                arguments = arguments,
                source = source
            ),
            source = source
        )
    } else {
        return JavascriptFunctionCallNode(
            function = handleReference,
            arguments = arguments,
            source = source
        )
    }
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
                        generateCodeForField(field, context, source)
                    },
                    source = source
                )
            ),
            source = source
        ),
        source = source
    )
}

private fun generateCodeForField(field: FieldInspector, context: CodeGenerationContext, source: NodeSource): JavascriptObjectLiteralNode {
    val fieldSource = field.source
    val fieldValue = field.value
    val value = when (fieldValue) {
        null ->
            // TODO: use undefined
            JavascriptNullLiteralNode(source = fieldSource)

        is FieldValue.Expression ->
            generateCode(fieldValue.expression, context)
    }

    val jsFieldName = generateName(field.name)
    return JavascriptObjectLiteralNode(
        elements = listOf(
            JavascriptPropertyNode(
                "get",
                generateFieldGetFunction(jsFieldName, fieldSource),
                source = fieldSource,
            ),
            JavascriptPropertyNode(
                "isConstant",
                JavascriptBooleanLiteralNode(
                    field.isConstant,
                    source = fieldSource,
                ),
                source = fieldSource,
            ),
            JavascriptPropertyNode(
                "jsName",
                JavascriptStringLiteralNode(
                    jsFieldName,
                    source = fieldSource
                ),
                source = fieldSource,
            ),
            JavascriptPropertyNode(
                "name",
                JavascriptStringLiteralNode(
                    field.name.value,
                    source = fieldSource
                ),
                source = fieldSource,
            ),
            JavascriptPropertyNode(
                "update",
                generateFieldUpdateFunction(jsFieldName, fieldSource),
                source = fieldSource,
            ),
            JavascriptPropertyNode("value", value, source = fieldSource),
        ),
        source = source
    )
}

private fun generateFieldGetFunction(jsFieldName: String, fieldSource: Source): JavascriptExpressionNode {
    return JavascriptFunctionExpressionNode(
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
    )
}

private fun generateFieldUpdateFunction(jsFieldName: String, fieldSource: Source): JavascriptExpressionNode {
    return JavascriptFunctionExpressionNode(
        parameters = listOf("newFieldValue", "existingObject"),
        body = listOf(
            JavascriptReturnNode(
                expression = JavascriptObjectLiteralNode(
                    elements = listOf(
                        JavascriptSpreadPropertiesNode(
                            expression = JavascriptVariableReferenceNode(
                                "existingObject",
                                source = fieldSource,
                            ),
                            source = fieldSource,
                        ),
                        JavascriptPropertyNode(
                            name = jsFieldName,
                            expression = JavascriptVariableReferenceNode(
                                "newFieldValue",
                                source = fieldSource,
                            ),
                            source = fieldSource,
                        ),
                    ),
                    source = fieldSource,
                ),
                source = fieldSource,
            )
        ),
        source = fieldSource,
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

private fun generateCodeForFunctionDefinition(node: FunctionDefinitionNode, context: CodeGenerationContext): List<JavascriptStatementNode> {
    val functionType = context.inspector.functionType(node)
    val source = NodeSource(node)
    val valueName = generateName(node.name)
    val value = generateCodeForFunction(
        name = valueName,
        node = node,
        functionType = functionType,
        context = context
    )

    return listOf(
        JavascriptConstNode(
            target = JavascriptVariableReferenceNode(valueName, source = source),
            expression = value,
            source = source
        )
    )
}

private fun generateCodeForFunction(name: String, node: FunctionNode, functionType: Type, context: CodeGenerationContext): JavascriptExpressionNode {
    val synchrony = functionSynchrony(functionType)
    val source = NodeSource(node)

    return when (synchrony) {
        Synchrony.AMBIGUOUS -> {
            immediatelyInvokedFunction(
                isAsync = false,
                body = listOf(
                    JavascriptConstNode(
                        target = JavascriptVariableReferenceNode(name, source = source),
                        expression = generateFunctionExpression(node, context, isAsync = false),
                        source = source
                    ),

                    JavascriptExpressionStatementNode(
                        JavascriptAssignmentNode(
                            target = JavascriptPropertyAccessNode(
                                receiver = JavascriptVariableReferenceNode(name, source = source),
                                propertyName = "async",
                                source = source
                            ),
                            expression = generateFunctionExpression(node, context, isAsync = true),
                            source = source
                        ),
                        source = source
                    ),

                    JavascriptReturnNode(
                        JavascriptVariableReferenceNode(name, source = source),
                        source = source
                    )
                ),
                source = source
            )
        }

        Synchrony.ASYNC -> JavascriptObjectLiteralNode(
            elements = listOf(
                JavascriptPropertyNode(
                    "async",
                    generateFunctionExpression(node, context, isAsync = true),
                    source = source,
                ),
            ),
            source = source,
        )

        Synchrony.SYNC -> {
            immediatelyInvokedFunction(
                isAsync = false,
                body = listOf(
                    JavascriptConstNode(
                        target = JavascriptVariableReferenceNode(name, source = source),
                        expression = generateFunctionExpression(node, context, isAsync = false),
                        source = source
                    ),

                    JavascriptExpressionStatementNode(
                        JavascriptAssignmentNode(
                            target = JavascriptPropertyAccessNode(
                                receiver = JavascriptVariableReferenceNode(name, source = source),
                                propertyName = "async",
                                source = source
                            ),
                            expression = generateFunctionExpression(node, context, isAsync = false),
                            source = source
                        ),
                        source = source
                    ),

                    JavascriptReturnNode(
                        JavascriptVariableReferenceNode(name, source = source),
                        source = source
                    )
                ),
                source = source
            )
        }
    }
}

private fun generateFunctionExpression(node: FunctionNode, context: CodeGenerationContext, isAsync: Boolean): JavascriptExpressionNode {
    val javascriptFunction = generateFunction(node, context, isAsync = isAsync)
    return JavascriptFunctionExpressionNode(
        isAsync = javascriptFunction.isAsync,
        parameters = javascriptFunction.parameters,
        body = javascriptFunction.body,
        source = node.source
    )
}

private fun generateFunction(node: FunctionNode, context: CodeGenerationContext, isAsync: Boolean): JavascriptFunctionNode {
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

    context.enterFunction(isAsync = isAsync)
    val body = namedParameterAssignments + generateBlockCode(node.body, context)
    context.exitFunction()

    return object: JavascriptFunctionNode {
        override val isAsync = isAsync
        override val parameters = positionalParameters + namedParameters
        override val body = body
    }
}

private fun generateBlockCode(block: Block?, context: CodeGenerationContext): List<JavascriptStatementNode> {
    return if (block == null) {
        listOf()
    } else {
        block.statements.flatMap { statement -> generateCodeForFunctionStatement(statement, context) }
    }
}

internal fun generateCodeForFunctionStatement(node: FunctionStatementNode, context: CodeGenerationContext): List<JavascriptStatementNode> {
    return node.accept(object : FunctionStatementNode.Visitor<List<JavascriptStatementNode>> {
        override fun visit(node: ExpressionStatementNode): List<JavascriptStatementNode> {
            val expression = generateCode(node.expression, context)
            val source = NodeSource(node)
            return when (node.type) {
                ExpressionStatementNode.Type.EXIT,
                ExpressionStatementNode.Type.TAILREC,
                ExpressionStatementNode.Type.VALUE ->
                    listOf(JavascriptReturnNode(expression, source))

                ExpressionStatementNode.Type.NO_VALUE ->
                    listOf(JavascriptExpressionStatementNode(expression, source))
            }
        }

        override fun visit(node: ResumeNode): List<JavascriptStatementNode> {
            throw NotImplementedError()
        }

        override fun visit(node: ValNode): List<JavascriptStatementNode> {
            return listOf(generateCode(node, context))
        }

        override fun visit(node: FunctionDefinitionNode): List<JavascriptStatementNode> {
            return generateCodeForFunctionDefinition(node, context)
        }

        override fun visit(node: ShapeNode): List<JavascriptStatementNode> {
            return listOf(generateCodeForShape(node, context))
        }

        override fun visit(node: EffectDefinitionNode): List<JavascriptStatementNode> {
            return generateCodeForEffectDefinition(node)
        }
    })
}

private fun generateCode(node: ValNode, context: CodeGenerationContext): JavascriptConstNode {
    val source = NodeSource(node)
    val target = node.target
    val javascriptTarget = generateCodeForTarget(target, context)
    return JavascriptConstNode(
        target = javascriptTarget,
        expression = generateCode(node.expression, context),
        source = source
    )
}

private fun generateCodeForTarget(
    shedTarget: TargetNode,
    context: CodeGenerationContext
): JavascriptTargetNode {
    val source = NodeSource(shedTarget)
    return when (shedTarget) {
        is TargetNode.Ignore ->
            JavascriptVariableReferenceNode(context.freshName("ignore"), source = source)

        is TargetNode.Variable -> {
            JavascriptVariableReferenceNode(generateName(shedTarget.name), source = source)
        }

        is TargetNode.Tuple -> JavascriptArrayDestructuringNode(
            elements = shedTarget.elements.map { targetElement ->
                generateCodeForTarget(targetElement, context)
            },
            source = source
        )

        is TargetNode.Fields -> JavascriptObjectDestructuringNode(
            properties = shedTarget.fields.map { (fieldName, fieldTarget) ->
                generateFieldName(fieldName) to generateCodeForTarget(fieldTarget, context)
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
            val expressionType = context.inspector.typeOfExpression(node) as FunctionType

            val receiver = generateCode(node.receiver, context)
            val positionalArguments = node.positionalArguments.map { argument ->
                generateCode(argument, context)
            }
            val namedArguments = node.fieldArguments
                .sortedBy { argument -> (argument as FieldArgumentNode.Named).name }
                .map { argument ->
                    JavascriptPropertyNode(
                        name = generateName((argument as FieldArgumentNode.Named).name),
                        expression = generateCode(argument.expression, context),
                        source = NodeSource(node),
                    )
                }

            val source = NodeSource(node)

            return JavascriptFunctionCallNode(
                function = JavascriptVariableReferenceNode(
                    "\$shed.partial",
                    source = source
                ),
                arguments = listOf(
                    receiver,
                    JavascriptArrayLiteralNode(elements = positionalArguments, source = source),
                    JavascriptObjectLiteralNode(elements = namedArguments, source = source),
                    JavascriptBooleanLiteralNode(expressionType.namedParameters.isNotEmpty(), source = source),
                ),
                source = source,
            )
        }

        override fun visit(node: StaticCallNode): JavascriptExpressionNode {
            return generateCode(node.receiver, context)
        }

        override fun visit(node: FieldAccessNode): JavascriptExpressionNode {
            return JavascriptPropertyAccessNode(
                generateCode(node.receiver, context),
                generateFieldName(node.fieldName),
                source = NodeSource(node)
            )
        }

        override fun visit(node: FunctionExpressionNode): JavascriptExpressionNode {
            return generateCodeForFunction(
                name = "anonymous",
                node = node,
                functionType = context.inspector.typeOfExpression(node),
                context = context
            )
        }

        override fun visit(node: IfNode): JavascriptExpressionNode {
            val source = NodeSource(node)

            context.enterFunction(isAsync = context.isAsync())
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
            context.exitFunction()

            return immediatelyInvokedFunction(
                isAsync = context.isAsync(),
                body = body,
                source = source
            )
        }

        override fun visit(node: WhenNode): JavascriptExpressionNode {
            val source = NodeSource(node)
            val temporaryName = "\$shed_tmp"

            context.enterFunction(isAsync = context.isAsync())
            val branches = node.conditionalBranches.map { branch ->
                val branchSource = NodeSource(branch)
                val discriminator = context.inspector.discriminatorForWhenBranch(node, branch)
                val condition = generateTypeCondition(
                    JavascriptVariableReferenceNode(
                        name = temporaryName,
                        source = branchSource
                    ),
                    discriminator,
                    branchSource
                )
                val target = if (branch.target.fields.isEmpty()) {
                    listOf()
                } else {
                    listOf(
                        JavascriptConstNode(
                            target = generateCodeForTarget(branch.target, context),
                            expression = JavascriptVariableReferenceNode(
                                name = temporaryName,
                                source = branchSource
                            ),
                            source = branchSource,
                        )
                    )
                }
                val body = target + generateBlockCode(branch.body, context)
                JavascriptConditionalBranchNode(
                    condition = condition,
                    body = body,
                    source = branchSource
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

            context.exitFunction()

            return immediatelyInvokedFunction(
                isAsync = context.isAsync(),
                body = body,
                source = source
            )
        }

        override fun visit(node: HandleNode): JavascriptExpressionNode {
            return generateCodeForHandle(node, context)
        }
    })
}

private fun generateCodeForCall(node: CallNode, context: CodeGenerationContext): JavascriptExpressionNode {
    val positionalArguments = node.positionalArguments.map { argument ->
        generateCode(argument, context)
    }
    val namedArguments = if (node.fieldArguments.isEmpty()) {
        listOf()
    } else {
        listOf(JavascriptObjectLiteralNode(
            node.fieldArguments.map { argument ->
                JavascriptPropertyNode(
                    // TODO: handle splat
                    generateName((argument as FieldArgumentNode.Named).name),
                    generateCode(argument.expression, context),
                    source = NodeSource(node),
                )
            },
            source = NodeSource(node)
        ))
    }
    val arguments = positionalArguments + namedArguments

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
        return await(operand = jsCall, source = NodeSource(node))
    } else {
        return jsCall
    }
}

private enum class Synchrony {
    AMBIGUOUS,
    ASYNC,
    SYNC
}

private fun isAsyncCall(node: CallNode, context: CodeGenerationContext): Boolean {
    val receiverType = context.inspector.typeOfExpression(node.receiver)
    val callSynchrony = functionSynchrony(receiverType)
    // TODO: in some cases, the receiver will be generic in an ambiguous synchrony context,
    // but we can statically determine the call to be synchronous (e.g. a call to map with a sync function).
    // Currently, we would consider this asynchronous, but we should consider it synchronous.
    // Perhaps store the inferred effects during type-checking?

    return when (callSynchrony) {
        Synchrony.AMBIGUOUS ->
            context.isAsync()

        Synchrony.ASYNC ->
            if (context.isAsync()) {
                true
            } else {
                // TODO: better error
                throw CompilerError("cannot call async function in synchronous context", source = node.source)
            }

        Synchrony.SYNC ->
            false
    }
}

private fun functionSynchrony(type: Type): Synchrony {
    val functionType = type as? FunctionType
    if (functionType == null) {
        return Synchrony.SYNC
    }

    if (isSubEffect(subEffect = IoEffect, superEffect = functionType.effect)) {
        return Synchrony.ASYNC
    } else if (hasGenericEffect(functionType.effect)) {
        return Synchrony.AMBIGUOUS
    } else {
        return Synchrony.SYNC
    }
}

private fun await(
    operand: JavascriptExpressionNode,
    source: Source
): JavascriptExpressionNode {
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
    source: Source
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
        return await(operand = call, source = source)
    } else {
        return call
    }
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
        BinaryOperator.DIVIDE -> throw NotImplementedError()
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
