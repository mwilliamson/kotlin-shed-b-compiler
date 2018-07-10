package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.*


internal fun typeCheck(statement: ModuleStatementNode, context: TypeContext) {
    return statement.accept(object : ModuleStatementNode.Visitor<Unit> {
        override fun visit(node: ShapeNode) = typeCheck(node, context)
        override fun visit(node: UnionNode) = typeCheck(node, context)
        override fun visit(node: FunctionDeclarationNode) = typeCheck(node, context)
        override fun visit(node: ValNode) = typeCheck(node, context)
    })
}

private fun typeCheck(node: ShapeNode, context: TypeContext) {
    val staticParameters = typeCheckStaticParameters(node.staticParameters, context)

    for ((fieldName, fields) in node.fields.groupBy({ field -> field.name })) {
        if (fields.size > 1) {
            throw FieldAlreadyDeclaredError(fieldName = fieldName, source = fields[1].source)
        }
    }

    val shapeId = freshShapeId()

    // TODO: test laziness
    val fields = lazy({
        // TODO: allow narrowing of fields
        val extendsFields = node.extends.flatMap { extendNode ->
            val superType = evalType(extendNode, context)
            if (superType is ShapeType) {
                superType.fields.values.map { field ->
                    FieldDefinition(field, extendNode.source)
                }
            } else {
                // TODO: throw a better exception
                throw NotImplementedError()
            }
        }

        val newFields = node.fields.map { field ->
            generateField(field, context, shapeId = shapeId)
        }

        mergeFields(extendsFields + newFields)
    })

    val shapeType = lazyShapeType(
        shapeId = shapeId,
        name = node.name,
        getFields = fields,
        staticParameters = staticParameters,
        staticArguments = staticParameters
    )
    val type = if (node.staticParameters.isEmpty()) {
        shapeType
    } else {
        TypeFunction(staticParameters, shapeType)
    }
    context.addVariableType(node, MetaType(type))
    context.defer({
        for (field in node.fields) {
            val fieldValue = field.value
            if (fieldValue != null) {
                verifyType(fieldValue, context, expected = shapeType.fields[field.name]!!.type)
            }
        }
        checkType(type, source = node.source)
    })
}

private data class FieldDefinition(val field: Field, val source: Source)

private fun generateField(field: ShapeFieldNode, context: TypeContext, shapeId: Int): FieldDefinition {
    val fieldTypeExpression = field.type
    val fieldType = if (fieldTypeExpression == null) {
        null
    } else {
        evalType(fieldTypeExpression, context)
    }

    val fieldValueExpression = field.value
    val valueType = if (fieldValueExpression == null) {
        null
    } else {
        inferType(fieldValueExpression, context)
    }

    val type = if (fieldType == null) {
        valueType
    } else {
        fieldType
    }
    return FieldDefinition(
        Field(
            shapeId = shapeId,
            name = field.name,
            // TODO: handle neither type nor value being set
            type = type!!,
            isConstant = field.value != null
        ),
        field.source
    )
}

private fun mergeFields(fields: List<FieldDefinition>): List<Field> {
    val fieldsByName = fields.groupBy { field -> field.field.name }
    return fieldsByName.map { (name, fieldsWithName) ->
        mergeField(name, fieldsWithName)
    }
}

private fun mergeField(name: Identifier, fields: List<FieldDefinition>): Field {
    if (fields.map { field -> field.field.shapeId }.distinct().size == 1) {
        val bottomFields = fields.filter { bottomField ->
            fields.all { upperField ->
                canCoerce(from = bottomField.field.type, to = upperField.field.type)
            }
        }
        if (bottomFields.size == 1) {
            return bottomFields.single().field
        } else {
            // TODO:
            throw NotImplementedError()
        }
    } else {
        throw FieldDeclarationConflictError(name = name, source = fields[1].source)
    }
}

private fun typeCheck(node: UnionNode, context: TypeContext) {
    // TODO: check for duplicates in members
    // TODO: check for circularity
    // TODO: test laziness
    // TODO: check members satisfy subtype relation
    val staticParameters = typeCheckStaticParameters(node.staticParameters, context)

    val superTypeNode = node.superType
    val superType = if (superTypeNode == null) {
        null
    } else {
        evalType(superTypeNode, context)
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
    val unionType = LazyUnionType(
        name = node.name,
        getMembers = members,
        staticArguments = staticParameters
    )
    val type = if (node.staticParameters.isEmpty()) {
        unionType
    } else {
        TypeFunction(staticParameters, unionType)
    }

    context.addVariableType(node, MetaType(type))
    context.defer({
        members.value
        checkType(type, source = node.source)
    })
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
