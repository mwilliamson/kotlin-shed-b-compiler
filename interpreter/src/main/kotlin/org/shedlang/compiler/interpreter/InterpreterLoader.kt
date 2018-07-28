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
        is Module.Native ->
            throw NotImplementedError()
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
        override fun visit(node: SymbolNode) = SymbolValue(context.moduleName, node.name)
        override fun visit(node: VariableReferenceNode) = VariableReference(node.name.value)

        override fun visit(node: BinaryOperationNode): Expression
            = BinaryOperation(
            node.operator,
            loadExpression(node.left, context),
            loadExpression(node.right, context)
        )

        override fun visit(node: IsNode): Expression {
            val sourceType = context.types.typeOf(node.expression)
            val targetType = rawType(metaTypeToType(context.types.typeOf(node.type))!!) as ShapeType
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
                positionalArgumentExpressions = node.positionalArguments.map { argument ->
                    loadExpression(argument, context)
                },
                positionalArgumentValues = listOf(),
                namedArgumentExpressions = node.namedArguments.map { argument ->
                    argument.name to loadExpression(argument.expression, context)
                },
                namedArgumentValues = listOf()
            )
        }

        override fun visit(node: PartialCallNode): Expression {
            throw UnsupportedOperationException("not implemented")
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
            throw UnsupportedOperationException("not implemented")
        }
    })
}

private data class Discriminator(val fieldName: Identifier, val symbolType: SymbolType)

private fun findDiscriminator(sourceType: Type, targetType: ShapeType): Discriminator? {
    // TODO: find discriminator properly (check that all members of
    // sourceType have the field, and that value is unique)
    for (field in targetType.fields.values) {
        val fieldType = field.type
        if (fieldType is SymbolType) {
            return Discriminator(field.name, fieldType)
        }
    }
    return null
}

private fun functionToExpression(node: FunctionNode, context: LoaderContext): FunctionExpression {
    return FunctionExpression(
        positionalParameterNames = node.parameters.map { parameter -> parameter.name.value },
        body = node.body.statements.map { statement ->
            loadStatement(statement, context)
        }
    )
}

private fun symbolTypeToValue(symbolType: SymbolType): SymbolValue {
    return SymbolValue(
        moduleName = symbolType.module.map(::Identifier),
        name = symbolType.name
    )
}
