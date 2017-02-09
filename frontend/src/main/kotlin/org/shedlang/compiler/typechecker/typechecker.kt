package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*

interface Type

object UnitType: Type
object BoolType : Type
object IntType : Type
object StringType : Type
object AnyType : Type
class MetaType(val type: Type): Type

data class FunctionType(
    val positionalArguments: List<Type>,
    val namedArguments: Map<String, Type>,
    val returns: Type
): Type

data class ShapeType(
    val name: String,
    val fields: Map<String, Type>
): Type

fun positionalFunctionType(arguments: List<Type>, returns: Type)
    = FunctionType(
        positionalArguments = arguments,
        namedArguments = mapOf(),
        returns = returns
    )

class TypeContext(
    val returnType: Type?,
    private val variables: MutableMap<Int, Type>,
    private val variableReferences: VariableReferences
) {
    fun typeOf(reference: ReferenceNode): Type {
        val targetNodeId = variableReferences[reference]
        val type = variables[targetNodeId]
        if (type == null) {
            throw CompilerError(
                "type of ${reference.name} is unknown",
                source = reference.source
            )
        } else {
            return type
        }
    }

    fun addTypes(types: Map<Int, Type>) {
        variables += types
    }

    fun enterFunction(returnType: Type): TypeContext {
        return TypeContext(
            returnType = returnType,
            variables = variables,
            variableReferences = variableReferences
        )
    }
}

/**
 * This indicates a bug in the compiler or its calling code
 */
open class CompilerError(message: String, val source: Source) : Exception(message)
class UnknownTypeError(val name: String, source: Source)
    : CompilerError("type of ${name} is unknown", source = source)
class NotFunctionTypeError(val actual: Type, source: Source)
    : TypeCheckError("expected function type but was ${actual}", source)

open class TypeCheckError(message: String?, val source: Source) : Exception(message)
internal class BadStatementError(source: Source)
    : TypeCheckError("Bad statement", source)
class UnresolvedReferenceError(val name: String, source: Source)
    : TypeCheckError("Unresolved reference: " + name, source)
class UninitialisedVariableError(val name: String, source: Source)
    : TypeCheckError("Uninitialised variable: " + name, source)
class RedeclarationError(val name: String, source: Source)
    : TypeCheckError("Variable with name ${name} has already been declared", source)
class UnexpectedTypeError(val expected: Type, val actual: Type, source: Source)
    : TypeCheckError("Expected type $expected but was $actual", source)
class WrongNumberOfArgumentsError(val expected: Int, val actual: Int, source: Source)
    : TypeCheckError("Expected $expected arguments, but got $actual", source)
class MissingArgumentError(val argumentName: String, source: Source)
    : TypeCheckError("Call is missing argument: $argumentName", source)
class ExtraArgumentError(val argumentName: String, source: Source)
    : TypeCheckError("Call has extra argument: $argumentName", source)
class PositionalArgumentPassedToShapeConstructorError(source: Source)
    : TypeCheckError("Positional arguments cannot be passed to shape constructors", source)
class ReturnOutsideOfFunctionError(source: Source)
    : TypeCheckError("Cannot return outside of a function", source)
class NoSuchFieldError(val fieldName: String, source: Source)
    : TypeCheckError("No such field: " + fieldName, source)

internal fun typeCheck(module: ModuleNode, context: TypeContext) {
    val (typeDeclarations, otherStatements) = module.body
        .partition({ statement -> statement is TypeDeclarationNode })

    for (typeDeclaration in typeDeclarations) {
        context.addTypes(mapOf(
            typeDeclaration.nodeId to inferType(typeDeclaration, context)
        ))
    }

    context.addTypes(otherStatements.associateBy(
        ModuleStatementNode::nodeId,
        { statement -> inferType(statement, context) }
    ))

    for (statement in module.body) {
        typeCheck(statement, context)
    }
}

internal fun inferType(statement: ModuleStatementNode, context: TypeContext): Type {
    return statement.accept(object : ModuleStatementNode.Visitor<Type> {
        override fun visit(node: ShapeNode): Type = inferType(node, context)
        override fun visit(node: FunctionNode): Type = inferType(node, context)
    })
}

internal fun typeCheck(statement: ModuleStatementNode, context: TypeContext) {
    return statement.accept(object : ModuleStatementNode.Visitor<Unit> {
        override fun visit(node: ShapeNode) {}
        override fun visit(node: FunctionNode): Unit = typeCheck(node, context)
    })
}

private fun inferType(node: ShapeNode, context: TypeContext): Type {
    val shapeType = ShapeType(
        name = node.name,
        fields = node.fields.associate({ field -> field.name to evalType(field.type, context) })
    )
    return MetaType(shapeType)
}

private fun inferType(function: FunctionNode, context: TypeContext): FunctionType {
    val argumentTypes = function.arguments.map(
        { argument -> evalType(argument.type, context) }
    )
    val returnType = evalType(function.returnType, context)
    return FunctionType(
        positionalArguments = argumentTypes,
        namedArguments = mapOf(),
        returns = returnType
    )
}

private fun typeCheck(function: FunctionNode, context: TypeContext) {
    val argumentTypes = function.arguments.associateBy(
        ArgumentNode::nodeId,
        { argument -> evalType(argument.type, context) }
    )
    val returnType = evalType(function.returnType, context)
    context.addTypes(argumentTypes)
    val bodyContext = context.enterFunction(returnType = returnType)
    typeCheck(function.body, bodyContext)
}

internal fun evalType(type: TypeNode, context: TypeContext): Type {
    return type.accept(object : TypeNode.Visitor<Type> {
        override fun visit(node: TypeReferenceNode): Type {
            val metaType = context.typeOf(node)
            return when (metaType) {
                is MetaType -> metaType.type
                else -> throw UnexpectedTypeError(
                    expected = MetaType(AnyType),
                    actual = metaType,
                    source = node.source
                )
            }
        }
    })
}

internal fun typeCheck(statement: StatementNode, context: TypeContext) {
    statement.accept(object : StatementNode.Visitor<Unit> {
        override fun visit(node: BadStatementNode) {
            throw BadStatementError(node.source)
        }

        override fun visit(node: IfStatementNode) {
            verifyType(node.condition, context, expected = BoolType)
            typeCheck(node.trueBranch, context)
            typeCheck(node.falseBranch, context)
        }

        override fun visit(node: ReturnNode): Unit {
            if (context.returnType == null) {
                throw ReturnOutsideOfFunctionError(node.source)
            } else {
                verifyType(node.expression, context, expected = context.returnType)
            }
        }

        override fun visit(node: ExpressionStatementNode) {
            typeCheck(node.expression, context)
        }

        override fun visit(node: ValNode) {
            val type = inferType(node.expression, context)
            context.addTypes(mapOf(node.nodeId to type))
        }
    })
}

private fun typeCheck(statements: List<StatementNode>, context: TypeContext) {
    for (statement in statements) {
        typeCheck(statement, context)
    }
}

private fun typeCheck(expression: ExpressionNode, context: TypeContext): Unit {
    inferType(expression, context)
}

internal fun inferType(expression: ExpressionNode, context: TypeContext) : Type {
    return expression.accept(object : ExpressionNode.Visitor<Type> {
        override fun visit(node: BooleanLiteralNode): Type {
            return BoolType
        }

        override fun visit(node: IntegerLiteralNode): Type {
            return IntType
        }

        override fun visit(node: StringLiteralNode): Type {
            return StringType
        }

        override fun visit(node: VariableReferenceNode): Type {
            return context.typeOf(node)
        }

        override fun visit(node: BinaryOperationNode): Type {
            verifyType(node.left, context, expected = IntType)
            verifyType(node.right, context, expected = IntType)
            return when (node.operator) {
                Operator.EQUALS -> BoolType
                Operator.ADD, Operator.SUBTRACT, Operator.MULTIPLY -> IntType
            }
        }

        override fun visit(node: FunctionCallNode): Type {
            val receiverType = inferType(node.function, context)
            if (receiverType is FunctionType) {
                node.positionalArguments.zip(receiverType.positionalArguments, { arg, argType -> verifyType(arg, context, expected = argType) })
                if (receiverType.positionalArguments.size != node.positionalArguments.size) {
                    throw WrongNumberOfArgumentsError(
                        expected = receiverType.positionalArguments.size,
                        actual = node.positionalArguments.size,
                        source = node.source
                    )
                }
                return receiverType.returns
            } else if (receiverType is MetaType && receiverType.type is ShapeType) {
                val shapeType = receiverType.type

                if (node.positionalArguments.any()) {
                    throw PositionalArgumentPassedToShapeConstructorError(source = node.positionalArguments.first().source)
                }

                for ((fieldName, type) in shapeType.fields) {
                    val actual = node.namedArguments[fieldName]
                    if (actual == null) {
                        throw MissingArgumentError(fieldName, source = node.source)
                    } else {
                        verifyType(actual, context, expected = type)
                    }
                }

                for ((argumentName, actual) in node.namedArguments) {
                    if (!shapeType.fields.containsKey(argumentName)) {
                        throw ExtraArgumentError(argumentName, source = actual.source)
                    }
                }

                return shapeType
            } else {
                val argumentTypes = node.positionalArguments.map { argument -> inferType(argument, context) }
                throw UnexpectedTypeError(
                    expected = FunctionType(argumentTypes, mapOf(), AnyType),
                    actual = receiverType,
                    source = node.function.source
                )
            }
        }

        override fun visit(node: FieldAccessNode): Type {
            val receiverType = inferType(node.receiver, context)
            if (receiverType is ShapeType) {
                val fieldType = receiverType.fields[node.fieldName]
                if (fieldType == null) {
                    throw NoSuchFieldError(
                        fieldName = node.fieldName,
                        source = node.source
                    )
                } else {
                    return fieldType
                }
            } else {
                throw NoSuchFieldError(
                    fieldName = node.fieldName,
                    source = node.source
                )
            }
        }
    })
}

private fun verifyType(expression: ExpressionNode, context: TypeContext, expected: Type) {
    val type = inferType(expression, context)
    verifyType(expected = expected, actual = type, source = expression.source)
}

private fun verifyType(expected: Type, actual: Type, source: Source) {
    if (actual != expected) {
        throw UnexpectedTypeError(expected = expected, actual = actual, source = source)
    }
}
