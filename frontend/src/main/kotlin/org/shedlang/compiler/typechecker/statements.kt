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

    val tag = if (node.tagged) {
        generateTag(node)
    } else {
        null
    }
    val shapeType = LazyShapeType(
        name = node.name,
        getFields = fields,
        typeParameters = typeParameters,
        typeArguments = typeParameters,
        declaredTagField = tag,
        getTagValue = lazy {
            if (node.hasTagValueFor == null) {
                null
            } else {
                val type = evalType(node.hasTagValueFor, context)
                val tag = getDeclaredTagField(type)
                if (tag == null) {
                    null
                } else {
                    TagValue(tagField = tag, tagValueId = node.nodeId)
                }
            }
        }
    )
    val type = if (node.typeParameters.isEmpty()) {
        shapeType
    } else {
        TypeFunction(typeParameters, shapeType)
    }
    context.addVariableType(node, MetaType(type))
    context.defer({
        fields.value
        checkType(type, source = node.source)
    })
}

private fun getDeclaredTagField(type: Type): TagField? {
    return if (type is TypeFunction) {
        getDeclaredTagField(type.type)
    } else if (type is MayDeclareTagField && type.declaredTagField != null) {
        type.declaredTagField
    } else {
        // TODO: throw a better exception
        throw Exception("TODO")
    }
}

private fun typeCheck(node: UnionNode, context: TypeContext) {
    // TODO: check for duplicates in members
    // TODO: check for circularity
    // TODO: test laziness
    // TODO: check members satisfy subtype relation
    val typeParameters = typeCheckTypeParameters(node.typeParameters, context)

    val superType = if (node.superType == null) {
        null
    } else {
        evalType(node.superType, context)
    }

    // TODO: check members conform to supertype

    val members = lazy({
        node.members.map({ member ->
            val memberType = evalType(member, context)
            if (memberType is ShapeType) {
                memberType
            } else {
                // TODO: test this, throw a sensible exception
                throw UnsupportedOperationException()
            }
        })
    })
    val tag = generateTag(node)
    val unionType = LazyUnionType(
        name = node.name,
        getMembers = members,
        typeArguments = typeParameters,
        declaredTagField = tag
    )
    val type = if (node.typeParameters.isEmpty()) {
        unionType
    } else {
        TypeFunction(typeParameters, unionType)
    }

    context.addVariableType(node, MetaType(type))
    context.defer({
        members.value
        checkType(type, source = node.source)
    })
}

private fun generateTag(node: VariableBindingNode): TagField {
    return TagField(name = node.name, tagFieldId = node.nodeId)
}

private fun typeCheck(function: FunctionDeclarationNode, context: TypeContext) {
    val type = typeCheckFunction(function, context)
    context.addVariableType(function, type)
}


internal fun typeCheck(statement: StatementNode, context: TypeContext): Type {
    return statement.accept(object : StatementNode.Visitor<Type> {
        override fun visit(node: BadStatementNode): Type {
            throw BadStatementError(node.source)
        }

        override fun visit(node: ExpressionStatementNode): Type {
            val type = inferType(node.expression, context)
            return if (node.isReturn) {
                type
            } else {
                UnitType
            }
        }

        override fun visit(node: ValNode): Type {
            typeCheck(node, context)
            return UnitType
        }
    })
}

private fun typeCheck(node: ValNode, context: TypeContext) {
    val type = inferType(node.expression, context)
    context.addVariableType(node, type)
}

internal fun typeCheck(statements: List<StatementNode>, context: TypeContext): Type {
    var type: Type = UnitType

    for (statement in statements) {
        type = typeCheck(statement, context)
    }

    return type
}
