package org.shedlang.compiler.interpreter

import org.shedlang.compiler.Module
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.Types
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.resolveImport
import org.shedlang.compiler.types.*


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
        import.name to ModuleReference(resolveImport(module.name, import.path))
    }
    val declarations = module.node.body.map { statement ->
        val name = statement.accept(object : ModuleStatementNode.Visitor<Identifier> {
            override fun visit(node: ShapeNode) = node.name
            override fun visit(node: UnionNode) = node.name
            override fun visit(node: FunctionDeclarationNode) = node.name
            override fun visit(node: ValNode) = node.name
        })
        val expression = loadModuleStatement(statement, context)
        name to expression
    }
    return ModuleExpression(fieldExpressions = imports + declarations, fieldValues = listOf())
}

internal fun loadModuleStatement(statement: ModuleStatementNode, context: LoaderContext): Expression {
    return statement.accept(object : ModuleStatementNode.Visitor<Expression> {
        override fun visit(node: ShapeNode): Expression {
            val type = rawType(context.types.declaredType(node)) as ShapeType

            val constantFields = type.fields.filter { (_, field) -> field.isConstant }.map { (name, field) ->
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

        override fun visit(node: UnionNode): Expression {
            return UnionTypeValue
        }

        override fun visit(node: FunctionDeclarationNode): Expression {
            return functionToExpression(node, context)
        }

        override fun visit(node: ValNode): Expression {
            return loadExpression(node.expression, context)
        }
    })
}

private fun loadStatement(statement: StatementNode, context: LoaderContext): Statement {
    return statement.accept(object: StatementNode.Visitor<Statement> {
        override fun visit(node: ExpressionStatementNode): Statement {
            return ExpressionStatement(
                expression = loadExpression(node.expression, context),
                isReturn = node.isReturn
            )
        }

        override fun visit(node: ValNode): Statement {
            return Val(
                name = node.name,
                expression = loadExpression(node.expression, context)
            )
        }
    })
}

internal fun loadExpression(expression: ExpressionNode, context: LoaderContext): Expression {
    return expression.accept(object : ExpressionNode.Visitor<Expression> {
        override fun visit(node: UnitLiteralNode) = UnitValue
        override fun visit(node: BooleanLiteralNode) = BooleanValue(node.value)
        override fun visit(node: IntegerLiteralNode) = IntegerValue(node.value)
        override fun visit(node: StringLiteralNode) = StringValue(node.value)
        override fun visit(node: CharacterLiteralNode) = CharacterValue(node.value)
        override fun visit(node: SymbolNode) = SymbolValue(Symbol(context.moduleName, node.name))
        override fun visit(node: VariableReferenceNode) = VariableReference(node.name.value)

        override fun visit(node: BinaryOperationNode): Expression
            = BinaryOperation(
            node.operator,
            loadExpression(node.left, context),
            loadExpression(node.right, context)
        )

        override fun visit(node: IsNode): Expression {
            val sourceType = context.types.typeOf(node.expression)
            val targetType = context.types.rawTypeValue(node.type) as ShapeType
            val discriminator = findDiscriminator(sourceType = sourceType, targetType = targetType)!!
            return BinaryOperation(
                Operator.EQUALS,
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
            return When(
                expression = loadExpression(node.expression, context),
                expressionType = context.types.typeOf(node.expression),
                branches = node.branches.map { branch ->
                    WhenBranch(
                        type = context.types.rawTypeValue(branch.type)!!,
                        body = branch.body.map { statement ->
                            loadStatement(statement, context)
                        }
                    )
                }
            )
        }
    })
}

private fun functionToExpression(node: FunctionNode, context: LoaderContext): FunctionExpression {
    return FunctionExpression(
        positionalParameterNames = node.parameters.map { parameter -> parameter.name.value },
        body = node.body.statements.map { statement ->
            loadStatement(statement, context)
        }
    )
}
