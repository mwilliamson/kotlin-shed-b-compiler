package org.shedlang.compiler.interpreter

import org.shedlang.compiler.Module
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.CodeInspector
import org.shedlang.compiler.backends.FieldValue
import org.shedlang.compiler.backends.ModuleCodeInspector
import org.shedlang.compiler.resolveImport
import org.shedlang.compiler.types.Discriminator
import org.shedlang.compiler.types.Symbol


internal class LoaderContext(val moduleName: List<Identifier>, val inspector: CodeInspector)


internal fun loadModuleSet(modules: ModuleSet): Map<List<Identifier>, ModuleExpression> {
    return modules.modules.associateBy(
        { module -> module.name },
        { module -> loadModule(module) }
    )
}

internal fun loadModule(module: Module): ModuleExpression {
    return when (module) {
        is Module.Shed ->
            loadModule(module)
        is Module.Native -> {
            val loadedModule = nativeModules[module.name]
            if (loadedModule == null) {
                throw InterpreterError("Could not find native module: " + module.name)
            } else {
                return loadedModule
            }
        }
    }
}

internal fun loadModule(module: Module.Shed): ModuleExpression {
    val context = LoaderContext(moduleName = module.name, inspector = ModuleCodeInspector(module))

    val imports = module.node.imports.map { import ->
        Val(loadTarget(import.target), ModuleReference(resolveImport(module.name, import.path)))
    }
    val declarations = module.node.body.flatMap { statement ->
        loadModuleStatement(statement, context)
    }
    return ModuleExpression(body = imports + declarations, fieldValues = listOf())
}

internal fun loadModuleStatement(statement: ModuleStatementNode, context: LoaderContext): List<Statement> {
    return statement.accept(object : ModuleStatementNode.Visitor<List<Statement>> {
        override fun visit(node: TypeAliasNode) = listOf(
            assign(node.name, TypeAliasTypeValue)
        )

        override fun visit(node: ShapeNode) = listOf(
            assign(node.name, shapeToExpression(node, context))
        )

        override fun visit(node: UnionNode) = listOf(assign(node.name, UnionTypeValue)) + node.members.map { member ->
            assign(member.name, shapeToExpression(member, context))
        }

        override fun visit(node: FunctionDeclarationNode) = listOf(
            assign(node.name, functionToExpression(node, context))
        )

        override fun visit(node: ValNode) = listOf(
            loadVal(node, context)
        )

        private fun assign(name: Identifier, value: Expression): Val {
            return Val(Target.Variable(name), value)
        }
    })
}

private fun shapeToExpression(node: ShapeBaseNode, context: LoaderContext): ShapeTypeValue {
    val fields = context.inspector.shapeFields(node)

    val constantFields = fields.mapNotNull { field ->
        val fieldValue = field.value
        val value = when (fieldValue) {
            null -> null

            is FieldValue.Expression ->
                loadExpression(fieldValue.expression, context) as InterpreterValue

            is FieldValue.Symbol ->
                SymbolValue(fieldValue.symbol)
        }
        if (value == null) {
            null
        } else {
            field.name to value
        }
    }

    val fieldsValue = ShapeValue(fields = fields.associate { field ->
        field.name to ShapeValue(fields = mapOf(
            Identifier("get") to FunctionValue(
                positionalParameterNames = listOf(Identifier("value")),
                body = listOf(
                    ExpressionStatement(
                        expression = FieldAccess(
                            receiver = VariableReference("value"),
                            fieldName = field.name
                        ),
                        isReturn = true
                    )
                ),
                outerScope = Scope(frameReferences = listOf())
            ),
            Identifier("name") to StringValue(field.name.value)
        ))
    })

    return ShapeTypeValue(
        constantFields = constantFields.toMap(),
        fields = mapOf(
            Identifier("fields") to fieldsValue
        )
    )
}

private fun loadStatement(statement: FunctionStatementNode, context: LoaderContext): Statement {
    return statement.accept(object: FunctionStatementNode.Visitor<Statement> {
        override fun visit(node: ExpressionStatementNode): Statement {
            return ExpressionStatement(
                expression = loadExpression(node.expression, context),
                isReturn = node.isReturn
            )
        }

        override fun visit(node: ValNode): Statement {
            return loadVal(node, context)
        }

        override fun visit(node: FunctionDeclarationNode): Statement {
            return Val(
                target = Target.Variable(node.name),
                expression = functionToExpression(node, context)
            )
        }
    })
}

private fun loadVal(node: ValNode, context: LoaderContext): Val {
    val targetNode = node.target

    val target = loadTarget(targetNode)

    return Val(
        target = target,
        expression = loadExpression(node.expression, context)
    )
}

private fun loadTarget(targetNode: TargetNode): Target {
    return when (targetNode) {
        is TargetNode.Variable -> Target.Variable(targetNode.name)
        is TargetNode.Tuple -> Target.Tuple(targetNode.elements.map { targetElement ->
            loadTarget(targetElement)
        })
        is TargetNode.Fields -> Target.Fields(targetNode.fields.map { (fieldName, fieldTarget) ->
            fieldName.identifier to loadTarget(fieldTarget)
        })
    }
}

internal fun loadExpression(expression: ExpressionNode, context: LoaderContext): Expression {
    return expression.accept(object : ExpressionNode.Visitor<Expression> {
        override fun visit(node: UnitLiteralNode) = UnitValue
        override fun visit(node: BooleanLiteralNode) = BooleanValue(node.value)
        override fun visit(node: IntegerLiteralNode) = IntegerValue(node.value)
        override fun visit(node: StringLiteralNode) = StringValue(node.value)
        override fun visit(node: CodePointLiteralNode) = CodePointValue(node.value)
        override fun visit(node: SymbolNode) = SymbolValue(Symbol(context.moduleName, node.name))

        override fun visit(node: TupleNode): Expression {
            return call(TupleConstructorValue, positionalArgumentExpressions = node.elements.map { element ->
                loadExpression(element, context)
            })
        }

        override fun visit(node: ReferenceNode) = VariableReference(node.name.value)

        override fun visit(node: UnaryOperationNode): Expression = UnaryOperation(
            node.operator,
            loadExpression(node.operand, context)
        )

        override fun visit(node: BinaryOperationNode): Expression
            = BinaryOperation(
            node.operator,
            loadExpression(node.left, context),
            loadExpression(node.right, context)
        )

        override fun visit(node: IsNode): Expression {
            val discriminator = context.inspector.discriminatorForIsExpression(node)
            return typeCondition(loadExpression(node.expression, context), discriminator)
        }

        override fun visit(node: CallNode): Expression {
            if (context.inspector.isCast(node)) {
                val discriminator = context.inspector.discriminatorForCast(node)
                val valueReference = VariableReference("value")
                return FunctionValue(
                    positionalParameterNames = listOf(Identifier("value")),
                    body = listOf(
                        ExpressionStatement(
                            expression = If(
                                conditionalBranches = listOf(
                                    ConditionalBranch(
                                        condition = typeCondition(valueReference, discriminator),
                                        body = listOf(
                                            ExpressionStatement(
                                                expression = Call(
                                                    receiver = optionsSomeReference,
                                                    positionalArgumentExpressions = listOf(valueReference),
                                                    namedArgumentExpressions = listOf(),
                                                    positionalArgumentValues = listOf(),
                                                    namedArgumentValues = listOf()
                                                ),
                                                isReturn = true
                                            )
                                        )
                                    )
                                ),
                                elseBranch = listOf(
                                    ExpressionStatement(
                                        expression = optionsNoneReference,
                                        isReturn = true
                                    )
                                )
                            ),
                            isReturn = true
                        )
                    ),
                    outerScope = Scope(listOf())
                )
            } else {
                return Call(
                    receiver = loadExpression(node.receiver, context),
                    positionalArgumentExpressions = loadPositionalArguments(node.positionalArguments),
                    positionalArgumentValues = listOf(),
                    namedArgumentExpressions = loadNamedArguments(node.namedArguments),
                    namedArgumentValues = listOf()
                )
            }
        }

        override fun visit(node: PartialCallNode): Expression {
            return Call(
                receiver = PartialCallFunctionValue,
                positionalArgumentExpressions = loadPositionalArguments(listOf(node.receiver) + node.positionalArguments),
                positionalArgumentValues = listOf(),
                namedArgumentExpressions = loadNamedArguments(node.namedArguments),
                namedArgumentValues = listOf()
            )
        }

        private fun loadPositionalArguments(arguments: List<ExpressionNode>): List<Expression> {
            return arguments.map { argument ->
                loadExpression(argument, context)
            }
        }

        private fun loadNamedArguments(arguments: List<CallNamedArgumentNode>): List<Pair<Identifier, Expression>> {
            return arguments.map { argument ->
                argument.name to loadExpression(argument.expression, context)
            }
        }

        override fun visit(node: FieldAccessNode): Expression {
            return FieldAccess(
                receiver = loadExpression(node.receiver, context),
                fieldName = node.fieldName.identifier
            )
        }

        override fun visit(node: FunctionExpressionNode): Expression {
            return functionToExpression(node, context)
        }

        override fun visit(node: IfNode): Expression {
            return If(
                conditionalBranches = node.conditionalBranches.map { branch ->
                    ConditionalBranch(
                        condition = loadExpression(branch.condition, context),
                        body = branch.body.statements.map { statement ->
                            loadStatement(statement, context)
                        }
                    )
                },
                elseBranch = node.elseBranch.statements.map { statement ->
                    loadStatement(statement, context)
                }
            )
        }

        override fun visit(node: WhenNode): Expression {
            val expressionName = "\$whenExpression"

            return DeferredBlock(listOf(
                Val(Target.Variable(Identifier(expressionName)), loadExpression(node.expression, context)),
                ExpressionStatement(isReturn = true, expression = If(
                    conditionalBranches = node.branches.map { branch ->
                        val discriminator = context.inspector.discriminatorForWhenBranch(node, branch)
                        val condition = typeCondition(VariableReference(expressionName), discriminator)
                        ConditionalBranch(
                            condition,
                            branch.body.statements.map { statement ->
                                loadStatement(statement, context)
                            }
                        )
                    },
                    elseBranch = node.elseBranch?.statements?.map { statement ->
                        loadStatement(statement, context)
                    } ?: listOf()
                ))
            ))
        }
    })
}

private fun typeCondition(
    expression: Expression,
    discriminator: Discriminator
): BinaryOperation {
    return BinaryOperation(
        BinaryOperator.EQUALS,
        FieldAccess(expression, discriminator.fieldName),
        symbolTypeToValue(discriminator.symbolType)
    )
}

private fun functionToExpression(node: FunctionNode, context: LoaderContext): FunctionExpression {
    return FunctionExpression(
        positionalParameterNames = node.parameters.map { parameter -> parameter.name },
        body = node.body.statements.map { statement ->
            loadStatement(statement, context)
        }
    )
}
