package org.shedlang.compiler.typechecker

import org.shedlang.compiler.CompilerError
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.*

private data class UnaryOperationType(val operator: UnaryOperator, val operand: Type)
private data class BinaryOperationType(val operator: BinaryOperator, val left: Type, val right: Type)

internal fun typeCheck(expression: ExpressionNode, context: TypeContext): Unit {
    inferType(expression, context)
}

internal fun verifyType(expression: ExpressionNode, context: TypeContext, expected: Type) {
    val type = inferType(expression, context)
    verifyType(expected = expected, actual = type, source = expression.source)
}

internal fun inferType(expression: ExpressionNode, context: TypeContext, hint: Type? = null) : Type {
    val type = expression.accept(object : ExpressionNode.Visitor<Type> {
        override fun visit(node: UnitLiteralNode) = UnitType
        override fun visit(node: BooleanLiteralNode) = BoolType
        override fun visit(node: IntegerLiteralNode) = IntType
        override fun visit(node: StringLiteralNode) = StringType
        override fun visit(node: UnicodeScalarLiteralNode) = UnicodeScalarType

        override fun visit(node: TupleNode): Type {
            return TupleType(node.elements.map { element ->
                inferType(element, context)
            })
        }

        override fun visit(node: ReferenceNode) = inferReferenceType(node, context)

        override fun visit(node: UnaryOperationNode): Type {
            return inferUnaryOperationType(node, context)
        }

        override fun visit(node: BinaryOperationNode): Type {
            return inferBinaryOperationType(node, context)
        }

        override fun visit(node: IsNode): Type {
            return inferIsExpressionType(node, context)
        }

        override fun visit(node: CallNode): Type {
            return inferCallType(node, context)
        }

        override fun visit(node: PartialCallNode): Type {
            return inferPartialCallType(node, context)
        }

        override fun visit(node: StaticCallNode): Type {
            return inferStaticCallType(node, context)
        }

        override fun visit(node: FieldAccessNode): Type {
            return inferFieldAccessType(node, context)
        }

        override fun visit(node: FunctionExpressionNode): Type {
            return typeCheckFunction(node, context, hint = hint)
        }

        override fun visit(node: IfNode): Type {
            return inferIfExpressionType(node, context)
        }

        override fun visit(node: WhenNode): Type {
            return inferWhenExpressionType(node, context)
        }

        override fun visit(node: HandleNode): Type {
            return inferHandleType(node, context)
        }
    })
    context.addExpressionType(expression, type)
    return type
}

private fun inferUnaryOperationType(node: UnaryOperationNode, context: TypeContext): Type {
    val operandType = inferType(node.operand, context)

    return when (UnaryOperationType(node.operator, unalias(operandType))) {
        UnaryOperationType(UnaryOperator.NOT, BoolType) -> BoolType
        UnaryOperationType(UnaryOperator.MINUS, IntType) -> IntType

        else -> throw InvalidUnaryOperationError(
            operator = node.operator,
            actualOperandType = operandType,
            source = node.operand.source
        )
    }
}

private fun inferBinaryOperationType(node: BinaryOperationNode, context: TypeContext): Type {
    val leftType = inferType(node.left, context)
    val rightType = inferType(node.right, context)

    return when (BinaryOperationType(node.operator, unalias(leftType), unalias(rightType))) {
        BinaryOperationType(BinaryOperator.EQUALS, IntType, IntType) -> BoolType
        BinaryOperationType(BinaryOperator.NOT_EQUAL, IntType, IntType) -> BoolType
        BinaryOperationType(BinaryOperator.ADD, IntType, IntType) -> IntType
        BinaryOperationType(BinaryOperator.SUBTRACT, IntType, IntType) -> IntType
        BinaryOperationType(BinaryOperator.MULTIPLY, IntType, IntType) -> IntType

        BinaryOperationType(BinaryOperator.EQUALS, StringType, StringType) -> BoolType
        BinaryOperationType(BinaryOperator.NOT_EQUAL, StringType, StringType) -> BoolType
        BinaryOperationType(BinaryOperator.ADD, StringType, StringType) -> StringType

        BinaryOperationType(BinaryOperator.EQUALS, UnicodeScalarType, UnicodeScalarType) -> BoolType
        BinaryOperationType(BinaryOperator.NOT_EQUAL, UnicodeScalarType, UnicodeScalarType) -> BoolType
        BinaryOperationType(BinaryOperator.LESS_THAN, UnicodeScalarType, UnicodeScalarType) -> BoolType
        BinaryOperationType(BinaryOperator.LESS_THAN_OR_EQUAL, UnicodeScalarType, UnicodeScalarType) -> BoolType
        BinaryOperationType(BinaryOperator.GREATER_THAN, UnicodeScalarType, UnicodeScalarType) -> BoolType
        BinaryOperationType(BinaryOperator.GREATER_THAN_OR_EQUAL, UnicodeScalarType, UnicodeScalarType) -> BoolType

        BinaryOperationType(BinaryOperator.EQUALS, BoolType, BoolType) -> BoolType
        BinaryOperationType(BinaryOperator.NOT_EQUAL, BoolType, BoolType) -> BoolType
        BinaryOperationType(BinaryOperator.AND, BoolType, BoolType) -> BoolType
        BinaryOperationType(BinaryOperator.OR, BoolType, BoolType) -> BoolType

        else -> throw InvalidBinaryOperationError(
            operator = node.operator,
            left = leftType,
            right = rightType,
            source = node.source
        )
    }
}

private fun inferIsExpressionType(node: IsNode, context: TypeContext): BoolType {
    // TODO: test expression and type checking

    val expressionType = checkTypeConditionOperand(node.expression, context)
    val targetType = evalStaticValue(node.type, context)

    val discriminator = evalTypeCondition(
        expressionType = expressionType,
        targetType = targetType,
        source = node.type.source
    )

    context.addDiscriminator(node, discriminator)

    return BoolType
}

private fun evalTypeCondition(
    expressionType: UnionType,
    targetType: StaticValue,
    source: Source
): Discriminator {
    // TODO: given generics are erased, when node.type is generic we
    // should make sure no other instantiations of that generic type
    // are possible e.g. if the expression has type Cons[T] | Nil,
    // then checking the type to be Cons[U] is valid iff T <: U

    val discriminator = findDiscriminator(sourceType = expressionType, targetType = targetType)
    if (discriminator == null) {
        throw CouldNotFindDiscriminator(sourceType = expressionType, targetType = targetType, source = source)
    }

    return discriminator
}

private fun inferFieldAccessType(node: FieldAccessNode, context: TypeContext): Type {
    val receiverType = inferType(node.receiver, context)
    return inferFieldAccessType(receiverType, node.fieldName)
}

internal fun inferFieldAccessType(receiverType: Type, fieldName: FieldNameNode): Type {
    val identifier = fieldName.identifier

    val fieldType = receiverType.fieldType(identifier)

    if (fieldType == null) {
        throw NoSuchFieldError(
            fieldName = fieldName.identifier,
            source = fieldName.source
        )
    } else {
        return fieldType
    }
}

private fun inferIfExpressionType(node: IfNode, context: TypeContext): Type {
    val conditionalBranchTypes = node.conditionalBranches.map { branch ->
        verifyType(branch.condition, context, expected = BoolType)

        val trueContext = context.enterScope()
        val condition = branch.condition

        if (condition is IsNode) {
            val conditionExpression = condition.expression
            if (conditionExpression is ReferenceNode) {
                val conditionType = evalStaticValue(condition.type, context)
                val discriminator = evalTypeCondition(
                    expressionType = context.typeOf(context.resolveReference(conditionExpression)) as UnionType,
                    targetType = conditionType,
                    source = branch.source
                )
                // TODO: test discriminator.targetType is used instead of conditionType
                trueContext.refineVariableType(conditionExpression, discriminator.targetType)
            }
        }

        typeCheckBlock(branch.body, trueContext)
    }
    val elseBranchType = typeCheckBlock(node.elseBranch, context)
    val branchTypes = conditionalBranchTypes + listOf(elseBranchType)

    return branchTypes.reduce(::union)
}

private fun inferWhenExpressionType(node: WhenNode, context: TypeContext): Type {
    val expressionType = checkTypeConditionOperand(node.expression, context)

    val branchResults = node.conditionalBranches.map { branch ->
        val conditionType = evalStaticValue(branch.type, context)
        val discriminator = evalTypeCondition(
            expressionType = expressionType,
            targetType = conditionType,
            source = branch.source
        )

        context.addDiscriminator(branch, discriminator)

        val branchContext = context.enterScope()

        val expression = node.expression
        if (expression is ReferenceNode) {
            // TODO: test discriminator.targetType is used instead of conditionType
            branchContext.refineVariableType(expression, discriminator.targetType)
        }

        typeCheckTarget(branch.target, discriminator.targetType, branchContext)

        val type = typeCheckBlock(branch.body, branchContext)
        Pair(type, discriminator.targetType)
    }

    var branchTypes = branchResults.map { result -> result.first }
    val caseTypes = branchResults.map { result -> result.second }

    val unhandledMembers = expressionType.members.filter { member ->
        caseTypes.all { caseType -> !isEquivalentType(caseType, member) }
    }

    val elseBranch = node.elseBranch
    if (elseBranch == null) {
        if (unhandledMembers.isNotEmpty()) {
            throw WhenIsNotExhaustiveError(unhandledMembers, source = node.source)
        }
    } else {
        if (unhandledMembers.isEmpty()) {
            // TODO: source should be else symbol
            throw WhenElseIsNotReachableError(source = node.source)
        }

        val branchContext = context.enterScope()
        val type = typeCheckBlock(elseBranch, branchContext)
        branchTypes += listOf(type)
    }

    return branchTypes.reduce(::union)
}

private fun checkTypeConditionOperand(expression: ExpressionNode, context: TypeContext): UnionType {
    val expressionType = inferType(expression, context)
    if (expressionType is UnionType) {
        return expressionType
    } else {
        throw UnexpectedTypeError(
            expected = UnionTypeGroup,
            actual = expressionType,
            source = expression.source
        )
    }
}

internal fun inferReferenceType(reference: ReferenceNode, context: TypeContext): Type {
    val targetNode = context.resolveReference(reference)
    return context.typeOf(targetNode)
}

private fun inferHandleType(node: HandleNode, context: TypeContext): Type {
    val effect = evalEffect(node.effect, context)

    if (effect !is UserDefinedEffect) {
        throw ExpectedUserDefinedEffectError(source = node.effect.source)
    }

    val initialState = node.initialState
    val stateType = if (initialState == null) {
        null
    } else {
        inferType(initialState, context)
    }

    val bodyContext = context.enterScope(extraEffect = effect)
    val bodyType = typeCheckBlock(node.body, bodyContext)
    val handlerReturnTypes = node.handlers.map { handler ->
        val operationType = effect.operations[handler.operationName]
        if (operationType == null) {
            throw UnknownOperationError(effect = effect, operationName = handler.operationName, source = node.source)
        }

        if (operationType.effect != effect) {
            throw CompilerError("operation has unexpected effect", source = node.source)
        }

        val handlerType = typeCheckFunction(
            handler.function,
            context,
            handle = HandleTypes(resumeValueType = operationType.returns, stateType = stateType),
            implicitEffect = context.effect
        )
        context.addExpressionType(handler.function, handlerType)

        verifyType(
            expected = operationType.copy(effect = context.effect, returns = AnyType),
            actual = handlerType,
            source = handler.source
        )

        handlerType.returns
    }

    val handlerNames = node.handlers.map { handler -> handler.operationName }
    val missingHandlerNames = effect.operations.keys.minus(handlerNames)
    if (missingHandlerNames.isNotEmpty()) {
        throw MissingHandlerError(missingHandlerNames.first(), source = node.source)
    }

    return unionAll(listOf(bodyType) + handlerReturnTypes)
}
