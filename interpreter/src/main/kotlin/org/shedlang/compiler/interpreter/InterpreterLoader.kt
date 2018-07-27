package org.shedlang.compiler.interpreter

import org.shedlang.compiler.Module
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.resolveImport


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
        is Module.Native ->
            throw NotImplementedError()
    }
}

internal fun loadModule(module: Module.Shed): ModuleExpression {
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
        val expression = statement.accept(object : ModuleStatementNode.Visitor<Expression> {
            override fun visit(node: ShapeNode): Expression {
                return ShapeTypeValue(
                    constantFields = node.fields
                        .mapNotNull { field ->
                            val expression = field.value
                            if (expression == null) {
                                null
                            } else {
                                field.name to loadExpression(module.name, expression) as InterpreterValue
                            }
                        }
                        .toMap()
                )
            }

            override fun visit(node: UnionNode): Expression {
                return UnionTypeValue
            }

            override fun visit(node: FunctionDeclarationNode): Expression {
                return FunctionValue(
                    positionalParameterNames = node.parameters.map { parameter -> parameter.name.value },
                    body = node.bodyStatements.map { statement ->
                        loadStatement(module.name, statement)
                    },
                    moduleName = module.name
                )
            }

            override fun visit(node: ValNode): Expression {
                return loadExpression(module.name, node.expression)
            }
        })
        name to expression
    }
    return ModuleExpression(fieldExpressions = imports + declarations, fieldValues = listOf())
}

private fun loadStatement(moduleName: List<Identifier>, statement: StatementNode): Statement {
    return statement.accept(object: StatementNode.Visitor<Statement> {
        override fun visit(node: ExpressionStatementNode): Statement {
            return ExpressionStatement(
                expression = loadExpression(moduleName, node.expression),
                isReturn = node.isReturn
            )
        }

        override fun visit(node: ValNode): Statement {
            return Val(
                name = node.name,
                expression = loadExpression(moduleName, node.expression)
            )
        }
    })
}

internal fun loadExpression(moduleName: List<Identifier>, expression: ExpressionNode): Expression {
    return expression.accept(object : ExpressionNode.Visitor<Expression> {
        override fun visit(node: UnitLiteralNode) = UnitValue
        override fun visit(node: BooleanLiteralNode) = BooleanValue(node.value)
        override fun visit(node: IntegerLiteralNode) = IntegerValue(node.value)
        override fun visit(node: StringLiteralNode) = StringValue(node.value)
        override fun visit(node: CharacterLiteralNode) = CharacterValue(node.value)
        override fun visit(node: SymbolNode) = SymbolValue(moduleName, node.name)
        override fun visit(node: VariableReferenceNode) = VariableReference(node.name.value)

        override fun visit(node: BinaryOperationNode): Expression
            = BinaryOperation(node.operator, loadExpression(moduleName, node.left), loadExpression(moduleName, node.right))

        override fun visit(node: IsNode): Expression {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: CallNode): Expression {
            return Call(
                receiver = loadExpression(moduleName, node.receiver),
                positionalArgumentExpressions = node.positionalArguments.map { argument ->
                    loadExpression(moduleName, argument)
                },
                positionalArgumentValues = listOf(),
                namedArgumentExpressions = node.namedArguments.map { argument ->
                    argument.name to loadExpression(moduleName, argument.expression)
                },
                namedArgumentValues = listOf()
            )
        }

        override fun visit(node: PartialCallNode): Expression {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: FieldAccessNode): Expression {
            return FieldAccess(
                receiver = loadExpression(moduleName, node.receiver),
                fieldName = node.fieldName.identifier
            )
        }

        override fun visit(node: FunctionExpressionNode): Expression {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: IfNode): Expression {
            return If(
                conditionalBranches = node.conditionalBranches.map { branch ->
                    ConditionalBranch(
                        condition = loadExpression(moduleName, branch.condition),
                        body = branch.body.map { statement ->
                            loadStatement(moduleName, statement)
                        }
                    )
                },
                elseBranch = node.elseBranch.map { statement ->
                    loadStatement(moduleName, statement)
                }
            )
        }

        override fun visit(node: WhenNode): Expression {
            throw UnsupportedOperationException("not implemented")
        }
    })
}
