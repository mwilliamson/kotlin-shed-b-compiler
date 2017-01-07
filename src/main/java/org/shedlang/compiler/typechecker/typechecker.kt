package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*
import java.util.*

interface Type

object UnitType: Type
object BoolType : Type
object IntType : Type
object AnyType : Type
class MetaType(val type: Type): Type

data class FunctionType(val arguments: List<Type>, val returns: Type): Type

class TypeContext(val returnType: Type?, private val variables: MutableMap<String, Type>) {
    fun typeOf(variable: VariableReferenceNode): Type? = typeOf(variable.name)
    fun typeOf(name: String): Type? = variables[name]

    fun enterFunction(returnType: Type): TypeContext {
        return TypeContext(returnType = returnType, variables = HashMap(variables))
    }
}

open class TypeCheckError(message: String?, val location: SourceLocation) : Exception(message)
class UnboundLocalError(val name: String, location: SourceLocation)
    : TypeCheckError("Local variable is not bound: " + name, location)
class UnexpectedTypeError(val expected: Type, val actual: Type, location: SourceLocation)
    : TypeCheckError("Expected type $expected but was $actual", location)
class WrongNumberOfArgumentsError(val expected: Int, val actual: Int, location: SourceLocation)
    : TypeCheckError("Expected $expected arguments, but got $actual", location)
class ReturnOutsideOfFunctionError(location: SourceLocation)
    : TypeCheckError("Cannot return outside of a function", location)

fun typeCheck(function: FunctionNode, context: TypeContext) {
    typeCheck(function.body, context.enterFunction(returnType = evalType(function.returnType, context)))
}

fun evalType(type: TypeNode, context: TypeContext): Type {
    return type.accept(object : TypeNode.Visitor<Type> {
        override fun visit(node: TypeReferenceNode): Type {
            // TODO: handle unbound
            val metaType = context.typeOf(node.name)!!
            return when (metaType) {
                is MetaType -> metaType.type
                // TODO: handle not a type
                else -> throw Exception()
            }
        }
    })
}

fun typeCheck(statement: StatementNode, context: TypeContext) {
    statement.accept(object : StatementNodeVisitor<Unit> {
        override fun visit(node: IfStatementNode) {
            verifyType(node.condition, context, expected = BoolType)
            typeCheck(node.trueBranch, context)
            typeCheck(node.falseBranch, context)
        }

        override fun visit(node: ReturnNode): Unit {
            if (context.returnType == null) {
                throw ReturnOutsideOfFunctionError(node.location)
            } else {
                verifyType(node.expression, context, expected = context.returnType)
            }
        }
    })
}

private fun typeCheck(statements: List<StatementNode>, context: TypeContext) {
    for (statement in statements) {
        typeCheck(statement, context)
    }
}

fun inferType(expression: ExpressionNode, context: TypeContext) : Type {
    return expression.accept(object : ExpressionNodeVisitor<Type> {
        override fun visit(node: BooleanLiteralNode): Type {
            return BoolType
        }

        override fun visit(node: IntegerLiteralNode): Type {
            return IntType
        }

        override fun visit(node: VariableReferenceNode): Type {
            val type = context.typeOf(node)
            if (type == null) {
                throw UnboundLocalError(node.name, node.location)
            } else {
                return type
            }
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
            val functionType = inferType(node.function, context)
            return when (functionType) {
                is FunctionType -> {
                    node.arguments.zip(functionType.arguments, { arg, argType -> verifyType(arg, context, expected = argType) })
                    if (functionType.arguments.size != node.arguments.size) {
                        throw WrongNumberOfArgumentsError(
                            expected = functionType.arguments.size,
                            actual = node.arguments.size,
                            location = node.location
                        )
                    }
                    functionType.returns
                }
                else -> {
                    val argumentTypes = node.arguments.map { argument -> inferType(argument, context) }
                    throw UnexpectedTypeError(
                        expected = FunctionType(argumentTypes, AnyType),
                        actual = functionType,
                        location = node.function.location
                    )
                }
            }
        }
    })
}

private fun verifyType(expression: ExpressionNode, context: TypeContext, expected: Type) {
    val type = inferType(expression, context)
    verifyType(expected = expected, actual = type, location = expression.location)
}

private fun verifyType(expected: Type, actual: Type, location: SourceLocation) {
    if (actual != expected) {
        throw UnexpectedTypeError(expected = expected, actual = actual, location = location)
    }
}
