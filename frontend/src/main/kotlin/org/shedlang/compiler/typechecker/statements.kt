package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.*
import typeCheckTypeParameters


internal fun typeCheck(statement: ModuleStatementNode, context: TypeContext) {
    return statement.accept(object : ModuleStatementNode.Visitor<Unit> {
        override fun visit(node: ShapeNode) = typeCheck(node, context)
        override fun visit(node: UnionNode) = typeCheck(node, context)
        override fun visit(node: FunctionDeclarationNode) = typeCheck(node, context)
        override fun visit(node: ValNode) = typeCheck(node, context)
    })
}

private fun typeCheck(node: ShapeNode, context: TypeContext) {
    val typeParameters = typeCheckTypeParameters(node.typeParameters, context)

    for ((fieldName, fields) in node.fields.groupBy({ field -> field.name })) {
        if (fields.size > 1) {
            throw FieldAlreadyDeclaredError(fieldName = fieldName, source = fields[1].source)
        }
    }

    // TODO: test laziness
    val fields = lazy({
        node.fields.associate({ field -> field.name to evalType(field.type, context) })
    })

    val shapeType = LazyShapeType(
        name = node.name,
        getFields = fields,
        typeParameters = typeParameters,
        typeArguments = typeParameters,
        tag = generateTag(node),
        getHasValueForTag = lazy {
            if (node.hasTagValueFor == null) {
                null
            } else {
                val type = evalType(node.hasTagValueFor, context)
                if (type is MayHaveTag && type.tag != null) {
                    type.tag
                } else {
                    // TODO: throw a better exception
                    throw Exception("TODO")
                }
            }
        }
    )
    val type = if (node.typeParameters.isEmpty()) {
        shapeType
    } else {
        TypeFunction(typeParameters, shapeType)
    }
    context.addType(node, MetaType(type))
    context.defer({
        fields.value
        checkType(type, source = node.source)
    })
}

private fun typeCheck(node: UnionNode, context: TypeContext) {
    // TODO: check for duplicates in members
    // TODO: check for circularity
    // TODO: test laziness
    val typeParameters = typeCheckTypeParameters(node.typeParameters, context)

    val members = lazy({ node.members.map({ member -> evalType(member, context) }) })
    val unionType = LazyUnionType(
        name = node.name,
        getMembers = members,
        typeArguments = typeParameters,
        tag = generateTag(node)
    )
    val type = if (node.typeParameters.isEmpty()) {
        unionType
    } else {
        TypeFunction(typeParameters, unionType)
    }

    context.addType(node, MetaType(type))
    context.defer({
        members.value
    })
}

private fun generateTag(node: MayHaveTagNode): Tag? {
    return if (node.tag) {
        Tag(name = node.name, tagId = node.nodeId)
    } else {
        null
    }
}

private fun typeCheck(function: FunctionDeclarationNode, context: TypeContext) {
    val type = typeCheckFunction(function, context)
    context.addType(function, type)
}


internal fun typeCheck(statement: StatementNode, context: TypeContext) {
    statement.accept(object : StatementNode.Visitor<Unit> {
        override fun visit(node: BadStatementNode) {
            throw BadStatementError(node.source)
        }

        override fun visit(node: IfStatementNode) {
            verifyType(node.condition, context, expected = BoolType)

            val trueContext = context.enterScope()

            if (node.condition is IsNode && node.condition.expression is VariableReferenceNode) {
                trueContext.addType(node.condition.expression, evalType(node.condition.type, context))
            }

            typeCheck(node.trueBranch, trueContext)
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
            typeCheck(node, context)
        }
    })
}

private fun typeCheck(node: ValNode, context: TypeContext) {
    val type = inferType(node.expression, context)
    context.addType(node, type)
}

internal fun typeCheck(statements: List<StatementNode>, context: TypeContext) {
    for (statement in statements) {
        typeCheck(statement, context)
    }
}
