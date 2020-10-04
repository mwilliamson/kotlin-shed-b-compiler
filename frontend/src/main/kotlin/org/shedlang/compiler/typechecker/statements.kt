package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.distinctWith
import org.shedlang.compiler.mapNullable
import org.shedlang.compiler.nullableToList
import org.shedlang.compiler.types.*


internal fun typeCheckModuleStatement(statement: ModuleStatementNode, context: TypeContext) {
    return statement.accept(object : ModuleStatementNode.Visitor<Unit> {
        override fun visit(node: EffectDefinitionNode) = typeCheckEffectDefinition(node, context)
        override fun visit(node: TypeAliasNode) = typeCheckTypeAlias(node, context)
        override fun visit(node: ShapeNode) = typeCheckShapeDefinition(node, context)
        override fun visit(node: UnionNode) = typeCheck(node, context)
        override fun visit(node: FunctionDefinitionNode) = typeCheckFunctionDefinition(node, context)
        override fun visit(node: ValNode) = typeCheck(node, context)
        override fun visit(node: VarargsDeclarationNode) = typeCheckVarargsDeclaration(node, context)
    })
}

private fun typeCheckEffectDefinition(node: EffectDefinitionNode, context: TypeContext) {
    var effect: UserDefinedEffect? = null
    effect = UserDefinedEffect(
        definitionId = node.nodeId,
        name = node.name,
        getOperations = lazy {
            node.operations.map { (operationName, operationTypeNode) ->
                // TODO: throw appropriate error on wrong type
                val operationType = evalStaticValue(operationTypeNode, context) as FunctionType
                operationName to operationType.copy(effect = effectUnion(operationType.effect, effect!!))
            }.toMap()
        }
    )
    context.addVariableType(node, StaticValueType(effect))
}

private fun typeCheckTypeAlias(node: TypeAliasNode, context: TypeContext) {
    // TODO: test laziness
    val type = LazyTypeAlias(
        name = node.name,
        getAliasedType = lazy {
            evalType(node.expression, context)
        }
    )
    context.addVariableType(node, StaticValueType(type))

    context.defer {
        type.aliasedType
    }
}

private fun typeCheckShapeDefinition(node: ShapeNode, context: TypeContext) {
    generateShapeType(node, context)
}

private fun generateShapeType(
    node: ShapeBaseNode,
    context: TypeContext,
    tagValue: TagValue? = null
): StaticValue {
    val staticParameters = typeCheckStaticParameters(node.staticParameters, context)

    val shapeId = freshTypeId()

    // TODO: test laziness
    val fields = generateFields(node, context, shapeId)

    val shapeType = lazyCompleteShapeType(
        shapeId = shapeId,
        name = node.name,
        tagValue = tagValue,
        getFields = fields,
        staticParameters = staticParameters,
        staticArguments = staticParameters
    )
    val type = if (node.staticParameters.isEmpty()) {
        shapeType
    } else {
        ParameterizedStaticValue(staticParameters, shapeType)
    }
    context.addVariableType(node, StaticValueType(type))
    context.defer({
        for (field in node.fields) {
            val fieldValue = field.value
            if (fieldValue != null) {
                verifyType(fieldValue, context, expected = shapeType.fieldType(field.name)!!)
            }
        }
        checkStaticValue(type, source = node.source)
    })
    return type
}

private fun generateFields(
    node: ShapeBaseNode,
    context: TypeContext,
    shapeId: Int
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
                superType.populatedFields.values.map { field ->
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

        mergeFields(parentFields, explicitFields)
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

    val baseShapeId = freshTypeId()

    val tag = Tag(context.moduleName!!, node.name)

    val memberTypes = node.members.map { member ->
        val tagValue = TagValue(tag, member.name)
        val type = generateShapeType(
            member,
            context,
            tagValue = tagValue
        )
        if (type is ShapeType) {
            type
        } else if (type is ParameterizedStaticValue) {
            applyStatic(type, type.parameters.map { shapeParameter ->
                // TODO: handle !!
                staticParameters.find { unionParameter -> unionParameter.name == shapeParameter.name }!!
            }) as ShapeType
        } else {
            throw UnsupportedOperationException()
        }
    }

    val unionType = LazyUnionType(
        name = node.name,
        tag = tag,
        getMembers = lazy { memberTypes },
        staticArguments = staticParameters
    )
    val type = if (node.staticParameters.isEmpty()) {
        unionType
    } else {
        ParameterizedStaticValue(staticParameters, unionType)
    }

    context.addVariableType(node, StaticValueType(type))
    context.defer({
        // TODO: checkStaticValue for member instead?
        memberTypes.forEach { memberType -> memberType.allFields }
        checkStaticValue(type, source = node.source)
    })
}

private fun typeCheckVarargsDeclaration(declaration: VarargsDeclarationNode, context: TypeContext) {
    // TODO: check other parts of function type (no effects, no other args, etc.)
    val type = VarargsType(
        name = declaration.name,
        // TODO: check properly
        cons = inferType(declaration.cons, context) as FunctionType,
        nil = inferType(declaration.nil, context)
    )
    context.addVariableType(declaration, type)
}

internal fun typeCheckFunctionDefinition(function: FunctionDefinitionNode, context: TypeContext) {
    val type = typeCheckFunction(function, context)
    context.addFunctionType(function, type)
    context.addVariableType(function, type)
}


internal fun typeCheckFunctionStatement(statement: FunctionStatementNode, context: TypeContext): Type {
    return statement.accept(object : FunctionStatementNode.Visitor<Type> {
        override fun visit(node: BadStatementNode): Type {
            throw BadStatementError(node.source)
        }

        override fun visit(node: ExpressionStatementNode): Type {
            val type = inferType(node.expression, context)

            return when (node.type) {
                ExpressionStatementNode.Type.EXIT,
                ExpressionStatementNode.Type.TAILREC,
                ExpressionStatementNode.Type.VALUE ->
                    type

                ExpressionStatementNode.Type.NO_VALUE ->
                    UnitType
            }
        }

        override fun visit(node: ResumeNode): Type {
            return typeCheckResume(node, context)
        }

        override fun visit(node: ValNode): Type {
            typeCheck(node, context)
            return UnitType
        }

        override fun visit(node: FunctionDefinitionNode): Type {
            typeCheckFunctionDefinition(node, context)
            return UnitType
        }

        override fun visit(node: ShapeNode): Type {
            typeCheckShapeDefinition(node, context)
            return UnitType
        }

        override fun visit(node: EffectDefinitionNode): Type {
            typeCheckEffectDefinition(node, context)
            return UnitType
        }
    })
}

private fun typeCheckResume(node: ResumeNode, context: TypeContext): NothingType {
    // TODO: check that we can resume in this context
    val handle = context.handle
    if (handle == null) {
        throw CannotResumeOutsideOfHandler(source = node.source)
    }

    verifyType(
        expression = node.expression,
        context = context,
        expected = handle.resumeValueType,
    )

    val newState = node.newState
    if (newState != null && handle.stateType == null) {
        throw CannotResumeWithStateInStatelessHandleError(source = node.source)
    } else if (newState == null && handle.stateType != null) {
        throw ResumeMissingNewStateError(source = node.source)
    } else if (newState != null && handle.stateType != null) {
        verifyType(
            expression = newState,
            context = context,
            expected = handle.stateType,
        )
    }

    return NothingType
}

private fun typeCheck(node: ValNode, context: TypeContext) {
    val type = inferType(node.expression, context)
    val target = node.target

    return typeCheckTarget(target, type, context)
}

internal fun typeCheckTarget(target: TargetNode, type: Type, context: TypeContext) {
    context.addTargetType(target, type)

    when (target) {
        is TargetNode.Ignore -> {}

        is TargetNode.Variable ->
            context.addVariableType(target, type)

        is TargetNode.Tuple -> {
            if (type !is TupleType || type.elementTypes.size != target.elements.size) {
                // TODO: should the error be on the target or expression?
                throw UnexpectedTypeError(
                    actual = TupleType(
                        elementTypes = target.elements.map { AnyType }
                    ),
                    expected = type,
                    source = target.source
                )
            }

            for ((elementType, targetElement) in type.elementTypes.zip(target.elements)) {
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

internal fun typeCheckBlock(block: Block, context: TypeContext): Type {
    var type: Type = UnitType

    for (statement in block.statements) {
        type = typeCheckFunctionStatement(statement, context)
    }

    return type
}
