package org.shedlang.compiler.interpreter

import org.shedlang.compiler.Module
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.*


internal fun loadModuleSet(modules: ModuleSet): Map<List<Identifier>, ModuleValue> {
    return modules.modules.associateBy(
        { module -> module.name },
        { module -> loadModule(module) }
    )
}

internal fun loadModule(module: Module): ModuleValue {
    return when (module) {
        is Module.Shed ->
            loadModule(module)
        is Module.Native ->
            throw NotImplementedError()
    }
}

internal fun loadModule(module: Module.Shed): ModuleValue {
    return ModuleValue(
        fields = module.node.body.associateBy(
            { statement -> statement.accept(object : ModuleStatementNode.Visitor<Identifier> {
                override fun visit(node: ShapeNode) = node.name
                override fun visit(node: UnionNode) = node.name
                override fun visit(node: FunctionDeclarationNode) = node.name
                override fun visit(node: ValNode) = node.name
            }) },
            { statement -> statement.accept(object: ModuleStatementNode.Visitor<InterpreterValue> {
                override fun visit(node: ShapeNode): InterpreterValue {
                    throw UnsupportedOperationException("not implemented")
                }

                override fun visit(node: UnionNode): InterpreterValue {
                    throw UnsupportedOperationException("not implemented")
                }

                override fun visit(node: FunctionDeclarationNode): InterpreterValue {
                    return FunctionValue(
                        body = node.bodyStatements.map { statement ->
                            loadStatement(statement)
                        }
                    )
                }

                override fun visit(node: ValNode): InterpreterValue {
                    throw UnsupportedOperationException("not implemented")
                }
            }) }
        )
    )
}

private fun loadStatement(statement: StatementNode): Statement {
    return statement.accept(object: StatementNode.Visitor<Statement> {
        override fun visit(node: ExpressionStatementNode): Statement {
            return ExpressionStatement(
                expression = loadExpression(node.expression),
                isReturn = node.isReturn
            )
        }

        override fun visit(node: ValNode): Statement {
            throw UnsupportedOperationException("not implemented")
        }

    })
}

internal fun loadExpression(expression: ExpressionNode): Expression {
    return expression.accept(object : ExpressionNode.Visitor<Expression> {
        override fun visit(node: UnitLiteralNode) = UnitValue
        override fun visit(node: BooleanLiteralNode) = BooleanValue(node.value)
        override fun visit(node: IntegerLiteralNode) = IntegerValue(node.value)
        override fun visit(node: StringLiteralNode) = StringValue(node.value)
        override fun visit(node: CharacterLiteralNode) = CharacterValue(node.value)
        override fun visit(node: SymbolNode) = SymbolValue(node.name)
        override fun visit(node: VariableReferenceNode) = VariableReference(node.name.value)

        override fun visit(node: BinaryOperationNode): Expression
            = BinaryOperation(node.operator, loadExpression(node.left), loadExpression(node.right))

        override fun visit(node: IsNode): Expression {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: CallNode): Expression {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: PartialCallNode): Expression {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: FieldAccessNode): Expression {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: FunctionExpressionNode): Expression {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: IfNode): Expression {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: WhenNode): Expression {
            throw UnsupportedOperationException("not implemented")
        }
    })
}
