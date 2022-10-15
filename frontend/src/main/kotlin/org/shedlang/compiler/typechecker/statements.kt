package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.distinctWith
import org.shedlang.compiler.nullableToList
import org.shedlang.compiler.types.*
import org.shedlang.compiler.util.Box


internal fun typeCheckModuleStatement(statement: ModuleStatementNode): TypeCheckSteps {
    return statement.accept(object : ModuleStatementNode.Visitor<TypeCheckSteps> {
        override fun visit(node: EffectDefinitionNode) = TypeCheckSteps(typeCheckEffectDefinitionSteps(node))
        override fun visit(node: TypeAliasNode) = typeCheckTypeAlias(node)
        override fun visit(node: ShapeNode) = TypeCheckSteps(typeCheckShapeDefinitionSteps(node))
        override fun visit(node: UnionNode) = typeCheckUnion(node)
        override fun visit(node: FunctionDefinitionNode) = TypeCheckSteps(typeCheckFunctionDefinitionSteps(node))
        override fun visit(node: ValNode) = TypeCheckSteps(typeCheckValSteps(node))
        override fun visit(node: VarargsDeclarationNode) = typeCheckVarargsDeclaration(node)
    })
}

internal enum class TypeCheckPhase {
    DEFINE_TYPE_LEVEL_VALUES,
    DEFINE_FUNCTIONS,
    GENERATE_TYPE_INFO,
    TYPE_CHECK_BODIES,
}

internal class TypeCheckStep(
    val phase: TypeCheckPhase,
    val func: (TypeContext) -> Unit
) {
    companion object {
        fun defineTypeLevelValues(func: (TypeContext) -> Unit): TypeCheckStep {
            return TypeCheckStep(phase = TypeCheckPhase.DEFINE_TYPE_LEVEL_VALUES, func)
        }

        fun defineFunctions(func: (TypeContext) -> Unit): TypeCheckStep {
            return TypeCheckStep(phase = TypeCheckPhase.DEFINE_FUNCTIONS, func)
        }

        fun generateTypeInfo(func: (TypeContext) -> Unit): TypeCheckStep {
            return TypeCheckStep(phase = TypeCheckPhase.GENERATE_TYPE_INFO, func)
        }

        fun typeCheckBodies(func: (TypeContext) -> Unit): TypeCheckStep {
            return TypeCheckStep(phase = TypeCheckPhase.TYPE_CHECK_BODIES, func)
        }
    }
}

internal class TypeCheckSteps(private val steps: List<TypeCheckStep>) {
    fun runAllPhases(context: TypeContext) {
        for (phase in TypeCheckPhase.values()) {
            run(phase, context)
        }
    }

    fun run(phase: TypeCheckPhase, context: TypeContext) {
        for (step in steps) {
            if (step.phase == phase) {
                step.func(context)
            }
        }
    }
}

private fun typeCheckEffectDefinitionSteps(node: EffectDefinitionNode): List<TypeCheckStep> {
    val effectBox = Box.mutable<UserDefinedEffect>()
    val operationsBox = Box.mutable<Map<Identifier, FunctionType>>()

    return listOf(
        TypeCheckStep.defineTypeLevelValues { context ->
            val effect = UserDefinedEffect(
                definitionId = node.nodeId,
                name = node.name,
                operationsBox = operationsBox
            )
            effectBox.set(effect)
            context.addVariableType(node, TypeLevelValueType(effect))
        },

        TypeCheckStep.generateTypeInfo { context ->
            val effect = effectBox.get()

            val operations = node.operations.map { (operationName, operationTypeNode) ->
                // TODO: throw appropriate error on wrong type
                val operationType = evalTypeLevelValue(operationTypeNode, context) as FunctionType
                operationName to operationType.copy(effect = effectUnion(operationType.effect, effect))
            }.toMap()

            operationsBox.set(operations)
        }
    )
}

private fun typeCheckTypeAlias(node: TypeAliasNode): TypeCheckSteps {
    val aliasedTypeBox = Box.mutable<Type>()

    return TypeCheckSteps(
        listOf(
            TypeCheckStep.defineTypeLevelValues { context ->
                val type = LazyTypeAlias(
                    name = node.name,
                    aliasedTypeBox = aliasedTypeBox
                )
                context.addVariableType(node, TypeLevelValueType(type))
            },

            TypeCheckStep.generateTypeInfo { context ->
                val aliasedType = evalType(node.expression, context)
                aliasedTypeBox.set(aliasedType)
            },
        )
    )
}

private fun typeCheckShapeDefinitionSteps(node: ShapeNode): List<TypeCheckStep> {
    return generateShapeTypeSteps(node)
}

private fun generateShapeTypeSteps(
    node: ShapeBaseNode,
    tagValueBox: Box<TagValue?> = Box.of(null)
): List<TypeCheckStep> {
    val shapeId = freshTypeId()
    val typeBox = Box.mutable<TypeLevelValue>()
    val fieldsBox = Box.mutable<List<Field>>()

    return listOf(
        TypeCheckStep.defineTypeLevelValues { context ->
            val tagValue = tagValueBox.get()

            val typeLevelParameters = typeCheckTypeLevelParameters(node.typeLevelParameters, context)

            val shapeType = shapeType(
                shapeId = shapeId,
                qualifiedName = context.qualifiedNameType(node.name),
                tagValue = tagValue,
                fieldsBox = fieldsBox,
            )
            val type = if (node.typeLevelParameters.isEmpty()) {
                shapeType
            } else {
                TypeConstructor(typeLevelParameters, shapeType)
            }
            typeBox.set(type)
            context.addVariableType(node, TypeLevelValueType(type))
        },

        TypeCheckStep.generateTypeInfo { context ->
            // TODO: test laziness
            val fields = generateFields(node, context, shapeId)
            fieldsBox.set(fields)
        },

        TypeCheckStep.typeCheckBodies { context ->
            val type = typeBox.get()
            checkTypeLevelValue(type, source = node.source)
        }
    )
}

private fun generateFields(
    node: ShapeBaseNode,
    context: TypeContext,
    shapeId: Int
): List<Field> {
    for ((fieldName, fields) in node.fields.groupBy({ field -> field.name })) {
        if (fields.size > 1) {
            throw FieldAlreadyDeclaredError(fieldName = fieldName, source = fields[1].source)
        }
    }

    val parentFields = node.extends.flatMap { extendNode ->
        val superType = evalType(extendNode, context)
        if (superType is SimpleShapeType) {
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

    return mergeFields(parentFields, explicitFields)
}

private data class FieldDefinition(val field: Field, val shape: Identifier, val source: Source)

private fun generateField(field: ShapeFieldNode, context: TypeContext, shapeId: Int, shapeName: Identifier): FieldDefinition {
    val fieldType = evalType(field.type, context)

    val fieldShapeExpression = field.shape
    val fieldShapeId = if (fieldShapeExpression == null) {
        shapeId
    } else {
        val fieldShapeType = evalType(fieldShapeExpression, context)
        if (fieldShapeType is SimpleShapeType) {
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
            type = fieldType,
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
            val bottomFields = parentFields.filter { bottomField ->
                parentFields.all { upperField ->
                    canCoerce(
                        from = bottomField.field.type,
                        to = upperField.field.type
                    )
                }
            }.distinctWith { first, second ->
                isEquivalentType(
                    first.field.type,
                    second.field.type
                )
            }
            if (bottomFields.size == 1) {
                return bottomFields.single().field
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
            }
            return newField.field
        }
    } else {
        throw FieldDeclarationShapeIdConflictError(name = name, source = fields[0].source)
    }
}

private fun typeCheckUnion(node: UnionNode): TypeCheckSteps {
    // TODO: check for duplicates in members
    // TODO: check for circularity
    // TODO: test laziness
    // TODO: check members satisfy subtype relation

    val tagBox = Box.mutable<Tag>()
    val typeLevelParametersBox = Box.mutable<List<TypeLevelParameter>>()
    val typeBox = Box.mutable<TypeLevelValue>()
    val membersBox = Box.mutable<List<ShapeType>>()

    val membersSteps = node.members.map { member ->
        val tagValueBox = tagBox.map { tag -> TagValue(tag, member.name) }
        TypeCheckSteps(generateShapeTypeSteps(
            member,
            tagValueBox = tagValueBox
        ))
    }

    return TypeCheckSteps(
        listOf(
            TypeCheckStep.defineTypeLevelValues { context ->
                val typeLevelParameters = typeCheckTypeLevelParameters(node.typeLevelParameters, context)
                typeLevelParametersBox.set(typeLevelParameters)

                val tag = Tag(context.qualifiedNameType(node.name))
                tagBox.set(tag)

                val unionType = LazySimpleUnionType(
                    name = node.name,
                    tag = tag,
                    getMembers = lazy { membersBox.get() },
                )

                val type = if (node.typeLevelParameters.isEmpty()) {
                    unionType
                } else {
                    TypeConstructor(typeLevelParameters, unionType)
                }
                typeBox.set(type)

                context.addVariableType(node, TypeLevelValueType(type))
            },

            TypeCheckStep.generateTypeInfo { context ->
                val typeLevelParameters = typeLevelParametersBox.get()

                val superTypeNode = node.superType
                val superType = if (superTypeNode == null) {
                    null
                } else {
                    evalType(superTypeNode, context)
                }

                val memberTypes = node.members.map { member ->
                    val type = (context.typeOf(member) as TypeLevelValueType).value
                    if (type is SimpleShapeType) {
                        type
                    } else if (type is TypeConstructor) {
                        applyTypeLevel(type, type.parameters.map { shapeParameter ->
                            // TODO: handle !!
                            typeLevelParameters.find { unionParameter -> unionParameter.name == shapeParameter.name }!!
                        }) as ShapeType
                    } else {
                        throw UnsupportedOperationException()
                    }
                }
                membersBox.set(memberTypes)
            },
        ) + TypeCheckPhase.values().map { phase ->
            TypeCheckStep(phase) { context ->
                for (memberSteps in membersSteps) {
                    memberSteps.run(phase, context)
                }
            }
        }
    )

//    val memberTypes = node.members.map { member ->
//        val tagValue = TagValue(tag, member.name)
//        val type = generateShapeType(
//            member,
//            context,
//            tagValue = tagValue
//        )
//        if (type is SimpleShapeType) {
//            type
//        } else if (type is TypeConstructor) {
//            applyTypeLevel(type, type.parameters.map { shapeParameter ->
//                // TODO: handle !!
//                typeLevelParameters.find { unionParameter -> unionParameter.name == shapeParameter.name }!!
//            }) as ShapeType
//        } else {
//            throw UnsupportedOperationException()
//        }
//    }
//
//    context.defer({
//        // TODO: checkStaticValue for member instead?
//        memberTypes.forEach { memberType -> memberType.fields }
//        checkTypeLevelValue(type, source = node.source)
//    })
}

private fun typeCheckVarargsDeclaration(declaration: VarargsDeclarationNode): TypeCheckSteps {
    // TODO: check other parts of function type (no effects, no other args, etc.)
    return TypeCheckSteps(
        listOf(
            TypeCheckStep.generateTypeInfo { context ->
                val type = VarargsType(
                    qualifiedName = context.qualifiedNameType(declaration.name),
                    // TODO: check properly
                    cons = inferType(declaration.cons, context) as FunctionType,
                    nil = inferType(declaration.nil, context)
                )
                context.addVariableType(declaration, type)
            }
        )
    )
}

internal fun typeCheckFunctionDefinitionSteps(function: FunctionDefinitionNode): List<TypeCheckStep> {
    val functionTypeCheckerBox = Box.mutable<FunctionTypeChecker>()

    return listOf(
        TypeCheckStep.defineFunctions { context ->
            val functionTypeChecker = typeCheckFunctionSignature(function, hint = null, context = context)
            functionTypeCheckerBox.set(functionTypeChecker)
            val type = functionTypeChecker.toFunctionType()

            context.addFunctionType(function, type)
            context.addVariableType(function, type)
        },

        TypeCheckStep.typeCheckBodies { context ->
            val functionTypeChecker = functionTypeCheckerBox.get()

            functionTypeChecker.typeCheckBody(context)
        }
    )
}

internal class FunctionStatementTypeChecker(
    private val steps: List<TypeCheckStep>,
    private val typeBox: Box<Type>
) {
    // TODO: Remove duplication with TypeCheckSteps
    fun run(phase: TypeCheckPhase, context: TypeContext) {
        for (step in steps) {
            if (step.phase == phase) {
                step.func(context)
            }
        }
    }

    fun type(): Type {
        return typeBox.get()
    }
}

internal fun typeCheckFunctionStatementSteps(statement: FunctionStatementNode): FunctionStatementTypeChecker {
    return statement.accept(object : FunctionStatementNode.Visitor<FunctionStatementTypeChecker> {
        override fun visit(node: BadStatementNode): FunctionStatementTypeChecker {
            throw BadStatementError(node.source)
        }

        override fun visit(node: ExpressionStatementNode): FunctionStatementTypeChecker {
            val typeBox = Box.mutable<Type>()

            return FunctionStatementTypeChecker(
                listOf(
                    TypeCheckStep.typeCheckBodies { context ->
                        val expressionType = inferType(node.expression, context)

                        val type = if (expressionType == NothingType) {
                            NothingType
                        } else {
                            when (node.type) {
                                ExpressionStatementNode.Type.EXIT,
                                ExpressionStatementNode.Type.TAILREC,
                                ExpressionStatementNode.Type.VALUE
                                ->
                                    expressionType

                                ExpressionStatementNode.Type.NO_VALUE ->
                                    UnitType
                            }
                        }
                        typeBox.set(type)
                    }
                ),
                typeBox,
            )
        }

        override fun visit(node: ResumeNode): FunctionStatementTypeChecker {
            return typeCheckResume(node)
        }

        override fun visit(node: ValNode): FunctionStatementTypeChecker {
            return FunctionStatementTypeChecker(
                typeCheckValSteps(node),
                Box.of(UnitType),
            )
        }

        override fun visit(node: FunctionDefinitionNode): FunctionStatementTypeChecker {
            return FunctionStatementTypeChecker(
                typeCheckFunctionDefinitionSteps(node),
                Box.of(UnitType),
            )
        }

        override fun visit(node: ShapeNode): FunctionStatementTypeChecker {
            return FunctionStatementTypeChecker(
                typeCheckShapeDefinitionSteps(node),
                Box.of(UnitType),
            )
        }

        override fun visit(node: EffectDefinitionNode): FunctionStatementTypeChecker {
            return FunctionStatementTypeChecker(
                typeCheckEffectDefinitionSteps(node),
                Box.of(UnitType),
            )
        }
    })
}

private fun typeCheckResume(node: ResumeNode): FunctionStatementTypeChecker {
    return FunctionStatementTypeChecker(
        listOf(
            TypeCheckStep.typeCheckBodies { context ->

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
            }
        ),

        Box.of(NothingType),
    )
}

private fun typeCheckValSteps(node: ValNode): List<TypeCheckStep> {
    return listOf(
        TypeCheckStep.generateTypeInfo { context ->
            val type = inferType(node.expression, context)

            typeCheckTarget(node.target, type, context)
        }
    )
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
                val fieldType = inferFieldAccessType(type, fieldName, context)
                typeCheckTarget(fieldTarget, fieldType, context)
            }
    }
}

internal fun typeCheckBlock(block: BlockNode, context: TypeContext): Type {
    val statementTypeCheckers = block.statements.map { statement -> typeCheckFunctionStatementSteps(statement) }

    for (phase in TypeCheckPhase.values()) {
        for (statementTypeChecker in statementTypeCheckers) {
            statementTypeChecker.run(phase, context)
        }
    }

    val lastStatementTypeChecker = statementTypeCheckers.lastOrNull()
    if (lastStatementTypeChecker == null) {
        return UnitType
    } else {
        return lastStatementTypeChecker.type()
    }
}
