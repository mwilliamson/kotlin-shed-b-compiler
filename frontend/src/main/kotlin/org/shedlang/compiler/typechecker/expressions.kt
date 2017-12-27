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

            // TODO: for this to be valid, the type must have a tag value

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

        override fun visit(node: IfNode): Type {
            val conditionalBranchTypes = node.conditionalBranches.map { branch ->
                verifyType(branch.condition, context, expected = BoolType)

                val trueContext = context.enterScope()

                if (
                    branch.condition is IsNode &&
                    branch.condition.expression is VariableReferenceNode
                ) {
                    val conditionType = evalType(branch.condition.type, context)
                    trueContext.addType(branch.condition.expression, conditionType)
                }

                typeCheck(branch.body, trueContext)
            }
            val elseBranchType = typeCheck(node.elseBranch, context)
            val branchTypes = conditionalBranchTypes + listOf(elseBranchType)

            return branchTypes.reduce(::union)
        }
    })
}
