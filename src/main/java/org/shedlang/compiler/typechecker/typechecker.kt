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

fun inferType(expression: ExpressionNode, context: TypeContext) : Type {
    return expression.visit(object : ExpressionNodeVisitor<Type> {
        override fun visit(node: BooleanLiteralNode): Type {
            return BoolType
        }

        override fun visit(node: IntegerLiteralNode): Type {
            return IntType
        }

        override fun visit(node: VariableReferenceNode): Type {
            // TODO: implement proper exception if variable not found
            return context.typeOf(node)!!
        }

        override fun visit(node: BinaryOperationNode): Type {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: FunctionCallNode): Type {
            throw UnsupportedOperationException("not implemented")
        }
    })
}
