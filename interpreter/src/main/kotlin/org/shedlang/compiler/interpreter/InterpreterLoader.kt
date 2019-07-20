package org.shedlang.compiler.interpreter

import org.shedlang.compiler.*
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.Symbol
import org.shedlang.compiler.types.SymbolType


internal class LoaderContext(val moduleName: List<Identifier>, val types: Types)


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
    val context = LoaderContext(moduleName = module.name, types = module.types)

    val imports = module.node.imports.map { import ->
        Val(Target.Variable(import.name), ModuleReference(resolveImport(module.name, import.path)))
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
    val fields = context.types.shapeFields(node)

    val constantFields = fields.filter { (_, field) -> field.isConstant }.map { (name, field) ->
        val fieldValueNode = node.fields
            .find { fieldNode -> fieldNode.name == name }
            ?.value
        val fieldType = field.type
        val value = if (fieldValueNode != null) {
            loadExpression(fieldValueNode, context) as InterpreterValue
        } else if (fieldType is SymbolType) {
            symbolTypeToValue(fieldType)
        } else {
            throw InterpreterError("Could not find value for constant field")
        }
        name to value
    }

    return ShapeTypeValue(
        constantFields = constantFields.toMap()
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

private fun loadTarget(targetNode: ValTargetNode): Target {
    return when (targetNode) {
        is ValTargetNode.Variable -> Target.Variable(targetNode.name)
        is ValTargetNode.Tuple -> Target.Tuple(targetNode.elements.map { targetElement ->
            loadTarget(targetElement)
        })
        is ValTargetNode.Fields -> throw NotImplementedError("TODO")
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

        override fun visit(node: VariableReferenceNode) = VariableReference(node.name.value)

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
            val discriminator = findDiscriminator(node, context.types)
            return BinaryOperation(
                BinaryOperator.EQUALS,
                FieldAccess(loadExpression(node.expression, context), discriminator.fieldName),
                symbolTypeToValue(discriminator.symbolType)
            )
        }

        override fun visit(node: CallNode): Expression {
            return Call(
                receiver = loadExpression(node.receiver, context),
                positionalArgumentExpressions = loadPositionalArguments(node.positionalArguments),
                positionalArgumentValues = listOf(),
                namedArgumentExpressions = loadNamedArguments(node.namedArguments),
                namedArgumentValues = listOf()
            )
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
                        body = branch.body.map { statement ->
                            loadStatement(statement, context)
                        }
                    )
                },
                elseBranch = node.elseBranch.map { statement ->
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
                        val discriminator = findDiscriminator(node, branch, context.types)
                        val condition = BinaryOperation(
                            BinaryOperator.EQUALS,
                            FieldAccess(VariableReference(expressionName), discriminator.fieldName),
                            symbolTypeToValue(discriminator.symbolType)
                        )
                        ConditionalBranch(
                            condition,
                            branch.body.map { statement ->
                                loadStatement(statement, context)
                            }
                        )
                    },
                    elseBranch = node.elseBranch.orEmpty().map { statement ->
                        loadStatement(statement, context)
                    }
                ))
            ))
        }
    })
}

private fun functionToExpression(node: FunctionNode, context: LoaderContext): FunctionExpression {
    return FunctionExpression(
        positionalParameterNames = node.parameters.map { parameter -> parameter.name },
        body = node.body.statements.map { statement ->
            loadStatement(statement, context)
        }
    )
}
