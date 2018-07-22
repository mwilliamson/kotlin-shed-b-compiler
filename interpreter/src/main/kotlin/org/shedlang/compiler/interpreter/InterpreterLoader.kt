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
        val expression = statement.accept(object : ModuleStatementNode.Visitor<InterpreterValue> {
            override fun visit(node: ShapeNode): InterpreterValue {
                throw UnsupportedOperationException("not implemented")
            }

            override fun visit(node: UnionNode): InterpreterValue {
                throw UnsupportedOperationException("not implemented")
            }

            override fun visit(node: FunctionDeclarationNode): InterpreterValue {
                return FunctionValue(
                    positionalParameterNames = node.parameters.map { parameter -> parameter.name.value },
                    body = node.bodyStatements.map { statement ->
                        loadStatement(statement)
                    },
                    moduleName = module.name
                )
            }

            override fun visit(node: ValNode): InterpreterValue {
                throw UnsupportedOperationException("not implemented")
            }
        })
        name to expression
    }
    return ModuleExpression(fieldExpressions = imports + declarations, fieldValues = listOf())
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
            return Call(
                receiver = loadExpression(node.receiver),
                positionalArgumentExpressions = node.positionalArguments.map(::loadExpression),
                positionalArgumentValues = listOf()
            )
        }

        override fun visit(node: PartialCallNode): Expression {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: FieldAccessNode): Expression {
            return FieldAccess(
                receiver = loadExpression(node.receiver),
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
                        condition = loadExpression(branch.condition),
                        body = branch.body.map(::loadStatement)
                    )
                },
                elseBranch = node.elseBranch.map(::loadStatement)
            )
        }

        override fun visit(node: WhenNode): Expression {
            throw UnsupportedOperationException("not implemented")
        }
    })
}
