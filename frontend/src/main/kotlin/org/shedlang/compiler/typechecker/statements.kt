package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.distinctWith
import org.shedlang.compiler.mapNullable
import org.shedlang.compiler.nullableToList
import org.shedlang.compiler.types.*


internal fun typeCheckModuleStatement(statement: ModuleStatementNode, context: TypeContext) {
    return statement.accept(object : ModuleStatementNode.Visitor<Unit> {
        override fun visit(node: TypeAliasNode) = typeCheckTypeAlias(node, context)
        override fun visit(node: ShapeNode) = typeCheck(node, context)
        override fun visit(node: UnionNode) = typeCheck(node, context)
        override fun visit(node: FunctionDeclarationNode) = typeCheckFunctionDeclaration(node, context)
        override fun visit(node: ValNode) = typeCheck(node, context)
    })
}

private fun typeCheckTypeAlias(node: TypeAliasNode, context: TypeContext) {
    // TODO: test laziness
    val type = LazyTypeAlias(
        name = node.name,
        getAliasedType = lazy {
            evalType(node.expression, context)
        }
    )
    context.addVariableType(node, MetaType(type))

    context.defer {
        type.aliasedType
    }
}

private fun typeCheck(node: ShapeNode, context: TypeContext) {
    generateShapeType(node, context)
}

private fun generateShapeType(
    node: ShapeBaseNode,
    context: TypeContext,
    extraFields: List<FieldDefinition> = listOf()
): Type {
    val staticParameters = typeCheckStaticParameters(node.staticParameters, context)

    val shapeId = freshShapeId()

    // TODO: test laziness
    val fields = generateFields(node, context, shapeId, extraFields)

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
    return type
}

private fun generateFields(
    node: ShapeBaseNode,
    context: TypeContext,
    shapeId: Int,
    extraFields: List<FieldDefinition>
): Lazy<List<Field>> {
    for ((fieldName, fields) in node.fields.groupBy({ field -> field.name })) {
        if (fields.size > 1) {
            throw FieldAlreadyDeclaredError(fieldName = fieldName, source = fields[1].source)
        }
    }

    return lazy {
        val parentFields = node.extends.flatMap { extendNode ->
            val superType = evalType(extendNode, context)
            if (superType is ShapeType) {
                superType.fields.values.map { field ->
                    FieldDefinition(field, superType.name, extendNode.source)
                }
            } else {
                // TODO: throw a better exception
                throw NotImplementedError()
            }
        }

        val explicitFields = node.fields.map { field ->
            generateField(field, context, shapeId = shapeId, shapeName = node.name)
        }

        mergeFields(parentFields, explicitFields + extraFields)
    }
}

private data class FieldDefinition(val field: Field, val shape: Identifier, val source: Source)

private fun generateField(field: ShapeFieldNode, context: TypeContext, shapeId: Int, shapeName: Identifier): FieldDefinition {
    val fieldType = field.type.mapNullable { expression ->
        evalType(expression, context)
    }
    val valueType = field.value.mapNullable { expression ->
        inferType(expression, context)
    }
    val type = fieldType ?: valueType

    val fieldShapeExpression = field.shape
    val fieldShapeId = if (fieldShapeExpression == null) {
        shapeId
    } else {
        val fieldShapeType = evalType(fieldShapeExpression, context)
        if (fieldShapeType is ShapeType) {
            fieldShapeType.shapeId
        } else {
            // TODO: throw a better error
            throw NotImplementedError()
        }
    }

    // TODO: check field type matches type in shape

    return FieldDefinition(
        Field(
            shapeId = fieldShapeId,
            name = field.name,
            // TODO: handle neither type nor value being set
            type = type!!,
            isConstant = field.value != null
        ),
        shapeName,
        field.source
    )
}

private fun mergeFields(parentFields: List<FieldDefinition>, newFields: List<FieldDefinition>): List<Field> {
    val parentFieldsByName = parentFields.groupBy { field -> field.field.name }
    val newFieldsByName = newFields.associateBy { field -> field.field.name }

    val fieldNames = (parentFieldsByName.keys + newFieldsByName.keys).sorted()

    return fieldNames.map { name ->
        mergeField(
            name,
            parentFieldsByName.getOrDefault(name, listOf()),
            newFieldsByName[name]
        )
    }
}

private fun mergeField(name: Identifier, parentFields: List<FieldDefinition>, newField: FieldDefinition?): Field {
    val fields = parentFields + newField.nullableToList()
    if (fields.map { field -> field.field.shapeId }.distinct().size == 1) {
        if (newField == null) {
            val constantFields = parentFields.filter { parentField -> parentField.field.isConstant }

            if (constantFields.size > 1) {
                throw FieldDeclarationValueConflictError(
                    name = name,
                    parentShape = constantFields[0].shape,
                    source = constantFields[1].source
                )
            }

            val bottomFieldCandiates = if (constantFields.size == 1) {
                constantFields
            } else {
                parentFields
            }

            val bottomFields = bottomFieldCandiates.filter { bottomField ->
                parentFields.all { upperField ->
                    canCoerce(from = bottomField.field.type, to = upperField.field.type)
                }
            }.distinctWith { first, second -> isEquivalentType(first.field.type, second.field.type) }
            if (bottomFields.size == 1) {
                val bottomField = bottomFields.single()
                for (parentField in parentFields) {
                    if (parentField.field.isConstant && parentField != bottomField) {
                        throw FieldDeclarationValueConflictError(
                            name = name,
                            parentShape = parentField.shape,
                            source = bottomField.source
                        )
                    }
                }
                return bottomField.field
            } else {
                throw FieldDeclarationMergeTypeConflictError(
                    name = name,
                    types = fields.map { field -> field.field.type },
                    source = fields[0].source
                )
            }
        } else {
            for (parentField in parentFields) {
                if (!canCoerce(from = newField.field.type, to = parentField.field.type)) {
                    throw FieldDeclarationOverrideTypeConflictError(
                        name = name,
                        overrideType = newField.field.type,
                        parentShape = parentField.shape,
                        parentType = parentField.field.type,
                        source = newField.source
                    )
                }
                if (parentField.field.isConstant) {
                    throw FieldDeclarationValueConflictError(
                        name = name,
                        parentShape = parentField.shape,
                        source = newField.source
                    )
                }
            }
            return newField.field
        }
    } else {
        throw FieldDeclarationShapeIdConflictError(name = name, source = fields[0].source)
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

    val baseShapeId = freshShapeId()

    val memberTypes = node.members.map { member ->
        val unionField = FieldDefinition(
            field = Field(
                shapeId = baseShapeId,
                name = Identifier("\$unionTag\$${context.moduleName!!.joinToString(".")}\$${node.name.value}"),
                isConstant = true,
                type = SymbolType(Symbol(context.moduleName.map(::Identifier), "@" + member.name.value))
            ),
            shape = node.name,
            source = node.source
        )
        val type = generateShapeType(
            member,
            context,
            extraFields = listOf(unionField)
        )
        if (type is ShapeType) {
            type
        } else if (type is TypeFunction) {
            applyStatic(type, type.parameters.map { shapeParameter ->
                staticParameters.find { unionParameter -> unionParameter.name == shapeParameter.name }!!
            }) as ShapeType
        } else {
            throw UnsupportedOperationException()
        }
    }

    val unionType = LazyUnionType(
        name = node.name,
        getMembers = lazy { memberTypes },
        staticArguments = staticParameters
    )
    val type = if (node.staticParameters.isEmpty()) {
        unionType
    } else {
        TypeFunction(staticParameters, unionType)
    }

    context.addVariableType(node, MetaType(type))
    context.defer({
        memberTypes.forEach { memberType -> memberType.fields }
        checkType(type, source = node.source)
    })
}

internal fun typeCheckFunctionDeclaration(function: FunctionDeclarationNode, context: TypeContext) {
    val type = typeCheckFunction(function, context)
    context.addVariableType(function, type)
}


internal fun typeCheckFunctionStatement(statement: FunctionStatementNode, context: TypeContext): Type {
    return statement.accept(object : FunctionStatementNode.Visitor<Type> {
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

        override fun visit(node: FunctionDeclarationNode): Type {
            typeCheckFunctionDeclaration(node, context)
            return UnitType
        }
    })
}

private fun typeCheck(node: ValNode, context: TypeContext) {
    val type = inferType(node.expression, context)
    val target = node.target

    return typeCheckTarget(target, type, context)
}

internal fun typeCheckTarget(target: TargetNode, type: Type, context: TypeContext) {
    when (target) {
        is TargetNode.Variable ->
            context.addVariableType(target, type)

        is TargetNode.Tuple -> {
            // TODO: check is tuple type
            val tupleType = type as TupleType

            if (tupleType.elementTypes.size != target.elements.size) {
                // TODO: should the error be on the target or expression?
                throw UnexpectedTypeError(
                    actual = TupleType(
                        elementTypes = target.elements.map { AnyType }
                    ),
                    expected = tupleType,
                    source = target.source
                )
            }

            for ((elementType, targetElement) in tupleType.elementTypes.zip(target.elements)) {
                typeCheckTarget(targetElement, elementType, context)
            }
        }

        is TargetNode.Fields ->
            for ((fieldName, fieldTarget) in target.fields) {
                val fieldType = inferFieldAccessType(type, fieldName)
                typeCheckTarget(fieldTarget, fieldType, context)
            }
    }
}

internal fun typeCheck(statements: List<FunctionStatementNode>, context: TypeContext): Type {
    var type: Type = UnitType

    for (statement in statements) {
        type = typeCheckFunctionStatement(statement, context)
    }

    return type
}
