package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.*

private data class OperationType(val operator: Operator, val left: Type, val right: Type)

internal fun typeCheck(expression: ExpressionNode, context: TypeContext): Unit {
    inferType(expression, context)
}

internal fun verifyType(expression: ExpressionNode, context: TypeContext, expected: Type) {
    val type = inferType(expression, context)
    verifyType(expected = expected, actual = type, source = expression.source)
}

internal fun inferType(expression: ExpressionNode, context: TypeContext, hint: Type? = null) : Type {
    return expression.accept(object : ExpressionNode.Visitor<Type> {
        override fun visit(node: UnitLiteralNode) = UnitType
        override fun visit(node: BooleanLiteralNode) = BoolType
        override fun visit(node: IntegerLiteralNode) = IntType
        override fun visit(node: StringLiteralNode) = StringType
        override fun visit(node: VariableReferenceNode) = context.typeOf(node)

        override fun visit(node: BinaryOperationNode): Type {
            val leftType = inferType(node.left, context)
            val rightType = inferType(node.right, context)

            return when (OperationType(node.operator, leftType, rightType)) {
                OperationType(Operator.EQUALS, IntType, IntType) -> BoolType
                OperationType(Operator.ADD, IntType, IntType) -> IntType
                OperationType(Operator.SUBTRACT, IntType, IntType) -> IntType
                OperationType(Operator.MULTIPLY, IntType, IntType) -> IntType

                OperationType(Operator.EQUALS, StringType, StringType) -> BoolType
                OperationType(Operator.ADD, StringType, StringType) -> StringType

                OperationType(Operator.EQUALS, BoolType, BoolType) -> BoolType

                else -> throw InvalidOperationError(
                    operator = node.operator,
                    operands = listOf(leftType, rightType),
                    source = node.source
                )
            }
        }

        override fun visit(node: IsNode): Type {
            // TODO: test expression and type checking

            typeCheck(node.expression, context)
            evalType(node.type, context)

            return BoolType
        }

        override fun visit(node: CallNode): Type {
            return inferType(node, context)
        }

        override fun visit(node: FieldAccessNode): Type {
            val receiverType = inferType(node.receiver, context)
            if (receiverType is HasFieldsType) {
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

        override fun visit(node: FunctionExpressionNode): Type {
            return typeCheckFunction(node, context, hint = hint)
        }
    })
}
