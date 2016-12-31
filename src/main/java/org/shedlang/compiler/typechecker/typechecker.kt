package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*

interface Type

object BoolType : Type
object IntType : Type

class TypeContext(private val variables: MutableMap<String, Type>) {
    fun typeOf(variable: VariableReferenceNode): Type? {
        return variables[variable.name]
    }
}

open class TypeCheckError(message: String?, val location: SourceLocation) : Exception(message)
class UnboundLocalError(val name: String, location: SourceLocation)
    : TypeCheckError("Local variable is not bound: " + name, location)
class UnexpectedTypeError(val expected: Type, val actual: Type, location: SourceLocation)
    : TypeCheckError("Expected type $expected but was $actual", location)

fun inferType(expression: ExpressionNode, context: TypeContext) : Type {
    return expression.visit(object : ExpressionNodeVisitor<Type> {
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
            return IntType
        }

        override fun visit(node: FunctionCallNode): Type {
            throw UnsupportedOperationException("not implemented")
        }
    })
}

private fun verifyType(expression: ExpressionNode, context: TypeContext, expected: Type) {
    val type = inferType(expression, context)
    if (type != expected) {
        throw UnexpectedTypeError(expected = expected, actual = type, location = expression.location)
    }
}
