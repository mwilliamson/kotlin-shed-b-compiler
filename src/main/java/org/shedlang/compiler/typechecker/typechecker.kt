package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*

interface Type

object BoolType : Type
object IntType : Type

class TypeContext(val returnType: Type?, private val variables: MutableMap<String, Type>) {
    fun typeOf(variable: VariableReferenceNode): Type? {
        return variables[variable.name]
    }
}

open class TypeCheckError(message: String?, val location: SourceLocation) : Exception(message)
class UnboundLocalError(val name: String, location: SourceLocation)
    : TypeCheckError("Local variable is not bound: " + name, location)
class UnexpectedTypeError(val expected: Type, val actual: Type, location: SourceLocation)
    : TypeCheckError("Expected type $expected but was $actual", location)
class ReturnOutsideOfFunctionError(location: SourceLocation)
    : TypeCheckError("Cannot return outside of a function", location)

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

fun typeCheck(statements: List<StatementNode>, context: TypeContext) {
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
            throw UnsupportedOperationException("not implemented")
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
