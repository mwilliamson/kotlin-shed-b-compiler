package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*

interface Type

object BoolType : Type
object IntType : Type

class TypeContext() {

}

fun inferType(expression: ExpressionNode, context: TypeContext) : Type {
    return expression.visit(object : ExpressionNodeVisitor<Type> {
        override fun visit(node: BooleanLiteralNode): Type {
            return BoolType
        }

        override fun visit(node: IntegerLiteralNode): Type {
            return IntType
        }

        override fun visit(node: VariableReferenceNode): Type {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: BinaryOperationNode): Type {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: FunctionCallNode): Type {
            throw UnsupportedOperationException("not implemented")
        }
    })
}
