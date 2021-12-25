package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.TypeCheckError
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.*
import org.shedlang.compiler.types.*

class TypeCheckShapeTests {
    @Test
    fun shapeDeclaresType() {
        val intType = staticReference("Int")
        val boolType = staticReference("Bool")
        val node = shape("X", fields = listOf(
            shapeField("a", intType, value = null),
            shapeField("b", boolType, value = literalBool())
        ))

        val typeContext = typeContext(referenceTypes = mapOf(
            intType to IntMetaType,
            boolType to BoolMetaType
        ))
        typeCheckModuleStatement(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(
            isShapeType(
                name = isIdentifier("X"),
                fields = isSequence(
                    isField(name = isIdentifier("a"), type = isIntType, isConstant = equalTo(false)),
                    isField(name = isIdentifier("b"), type = isBoolType, isConstant = equalTo(true))
                )
            )
        ))
    }

    @Test
    fun whenFieldValueDisagreesWithTypeThenErrorIsThrown() {
        val intType = staticReference("Int")
        val node = shape("X", fields = listOf(
            shapeField("a", intType, value = literalBool())
        ))

        val typeContext = typeContext(referenceTypes = mapOf(
            intType to IntMetaType
        ))
        assertThat(
            {
                typeCheckModuleStatement(node, typeContext)
                typeContext.undefer()
            },
            throwsUnexpectedType(
                expected = cast(isIntType),
                actual = isBoolType
            )
        )
    }

    @Test
    fun whenFieldHasNoTypeThenTypeIsInferredFromValue() {
        val node = shape("X", fields = listOf(
            shapeField("a", type = null, value = literalBool())
        ))

        val typeContext = typeContext()
        typeCheckModuleStatement(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(
            isShapeType(
                name = isIdentifier("X"),
                fields = isSequence(
                    isField(name = isIdentifier("a"), type = isBoolType, isConstant = equalTo(true))
                )
            )
        ))
    }

    @Test
    fun whenShapeDeclaresMultipleFieldsWithSameNameThenExceptionIsThrown() {
        val intType = staticReference("Int")
        val node = shape("X", fields = listOf(
            shapeField("a", intType),
            shapeField("a", intType)
        ))

        val typeContext = typeContext(referenceTypes = mapOf(
            intType to IntMetaType
        ))

        assertThat(
            { typeCheckModuleStatement(node, typeContext) },
            throws(
                has(FieldAlreadyDeclaredError::fieldName, isIdentifier("a"))
            )
        )
    }

    @Test
    fun shapeIncludesFieldsFromExtendedShapes() {
        val intType = staticReference("Int")
        val extendsShape1Reference = staticReference("Extends1")
        val extendsShape2Reference = staticReference("Extends2")

        val shape1Id = freshTypeId()
        val shape1 = shapeType(
            fields = listOf(
                field(name = "b", type = BoolType, shapeId = shape1Id, isConstant = true)
            )
        )

        val shape2Id = freshTypeId()
        val shape2 = shapeType(
            fields = listOf(
                field(name = "c", type = StringType, shapeId = shape2Id, isConstant = false)
            )
        )

        val node = shape(
            extends = listOf(
                extendsShape1Reference,
                extendsShape2Reference
            ),
            fields = listOf(
                shapeField("a", intType)
            )
        )

        val typeContext = typeContext(referenceTypes = mapOf(
            intType to IntMetaType,
            extendsShape1Reference to metaType(shape1),
            extendsShape2Reference to metaType(shape2)
        ))
        typeCheckModuleStatement(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(
            isShapeType(
                fields = isSequence(
                    isField(name = isIdentifier("a"), type = isIntType),
                    isField(shapeId = equalTo(shape1Id), name = isIdentifier("b"), type = isBoolType, isConstant = equalTo(true)),
                    isField(shapeId = equalTo(shape2Id), name = isIdentifier("c"), type = isStringType, isConstant = equalTo(false))
                )
            )
        ))
    }

    @Test
    fun whenFieldsWithSameNameHaveDifferentShapeIdsThenErrorIsThrown() {
        val shape1Id = freshTypeId()
        val firstField = field(name = "a", type = BoolType, shapeId = shape1Id, isConstant = true)

        val shape2Id = freshTypeId()
        val secondField = field(name = "a", type = StringType, shapeId = shape2Id, isConstant = false)

        assertThat(
            { mergeFields(firstField, secondField) },
            throws(
                has(FieldDeclarationShapeIdConflictError::name, isIdentifier("a"))
            )
        )
    }

    @Test
    fun whenFieldTypesAreNarrowedThenFinalFieldHasNarrowedType() {
        val shapeId = freshTypeId()
        val firstField = field(name = "a", type = AnyType, shapeId = shapeId)
        val secondField = field(name = "a", type = StringType, shapeId = shapeId)

        assertThat(
            mergeFields(firstField, secondField),
            isField(name = isIdentifier("a"), type = isStringType, shapeId = equalTo(shapeId))
        )
        assertThat(
            mergeFields(secondField, firstField),
            isField(name = isIdentifier("a"), type = isStringType, shapeId = equalTo(shapeId))
        )
    }

    @Test
    fun whenFieldTypesAreTheSameThenFinalFieldHasSameType() {
        val shapeId = freshTypeId()
        val firstField = field(name = "a", type = StringType, shapeId = shapeId)
        val secondField = field(name = "a", type = StringType, shapeId = shapeId)

        assertThat(
            mergeFields(firstField, secondField),
            isField(name = isIdentifier("a"), type = isStringType, shapeId = equalTo(shapeId))
        )
        assertThat(
            mergeFields(secondField, firstField),
            isField(name = isIdentifier("a"), type = isStringType, shapeId = equalTo(shapeId))
        )
    }

    @Test
    fun whenFieldIsDefinedWithConflictingTypesThenErrorIsThrown() {
        val shapeId = freshTypeId()
        val firstField = field(name = "a", type = BoolType, shapeId = shapeId)
        val secondField = field(name = "a", type = StringType, shapeId = shapeId)

        assertThat(
            { mergeFields(firstField, secondField) },
            throws(allOf(
                has(FieldDeclarationMergeTypeConflictError::name, isIdentifier("a")),
                has(FieldDeclarationMergeTypeConflictError::types, isSequence(isBoolType, isStringType))
            ))
        )
    }

    @Test
    fun constantFieldsArePreferredAsBottomFields() {
        val shapeId = freshTypeId()
        val firstField = field(name = "a", type = AnyType, shapeId = shapeId, isConstant = true)
        val secondField = field(name = "a", type = StringType, shapeId = shapeId, isConstant = false)

        assertThat(
            { mergeFields(firstField, secondField) },
            throws(allOf(
                has(FieldDeclarationMergeTypeConflictError::name, isIdentifier("a")),
                has(FieldDeclarationMergeTypeConflictError::types, isSequence(isAnyType, isStringType))
            ))
        )
    }

    @Test
    fun cannotMergeConstantFields() {
        val shapeId = freshTypeId()
        val firstField = field(name = "a", type = StringType, shapeId = shapeId, isConstant = true)
        val secondField = field(name = "a", type = StringType, shapeId = shapeId, isConstant = true)

        assertThat(
            { mergeFields(firstField, secondField) },
            throws(allOf(
                has(FieldDeclarationValueConflictError::name, isIdentifier("a")),
                has(FieldDeclarationValueConflictError::parentShape, isIdentifier("Extends1"))
            ))
        )
    }

    @Test
    fun constantFieldTakesPrecedenceWhenMergingFields() {
        val shapeId = freshTypeId()
        val firstField = field(name = "a", type = StringType, shapeId = shapeId, isConstant = true)
        val secondField = field(name = "a", type = StringType, shapeId = shapeId, isConstant = false)

        assertThat(
            mergeFields(firstField, secondField),
            isField(name = isIdentifier("a"), type = isStringType, shapeId = equalTo(shapeId), isConstant = equalTo(true))
        )
        assertThat(
            mergeFields(secondField, firstField),
            isField(name = isIdentifier("a"), type = isStringType, shapeId = equalTo(shapeId), isConstant = equalTo(true))
        )
    }

    private fun mergeFields(first: Field, second: Field): Field {
        val extendsShape1Reference = staticReference("Extends1")
        val extendsShape2Reference = staticReference("Extends2")

        val shape1 = shapeType(name = "Extends1", fields = listOf(first))
        val shape2 = shapeType(name = "Extends2", fields = listOf(second))

        val node = shape(
            extends = listOf(
                extendsShape1Reference,
                extendsShape2Reference
            ),
            fields = listOf()
        )

        val typeContext = typeContext(referenceTypes = mapOf(
            extendsShape1Reference to metaType(shape1),
            extendsShape2Reference to metaType(shape2)
        ))
        typeCheckModuleStatement(node, typeContext)
        typeContext.undefer()

        val metaType = typeContext.typeOf(node)
        assertThat(metaType, isMetaType(isShapeType()))
        val type = metaTypeToType(metaType)
        return (type as ShapeType).fields.values.single()
    }

    @Test
    fun shapeCannotOverrideFieldWithDifferentShape() {
        val shapeId = freshTypeId()
        val stringTypeReference = staticReference("String")

        val firstField = field(name = "a", type = AnyType, shapeId = shapeId)

        val extendsShapeReference = staticReference("Extends")
        val shape = shapeType(fields = listOf(firstField))
        val node = shape(
            extends = listOf(extendsShapeReference),
            fields = listOf(shapeField(shape = null, name = "a", type = stringTypeReference))
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            extendsShapeReference to metaType(shape),
            stringTypeReference to StringMetaType
        ))
        assertThat(
            {
                typeCheckModuleStatement(node, typeContext)
                typeContext.undefer()
            },
            throws(
                has(FieldDeclarationShapeIdConflictError::name, isIdentifier("a"))
            )
        )
    }

    @Test
    fun shapeCanOverrideFieldWithSubtypeUsingSameShape() {
        val shapeId = freshTypeId()
        val baseReference = staticReference("Base")
        val base = shapeType(shapeId = shapeId)
        val stringTypeReference = staticReference("String")

        val firstField = field(name = "a", type = AnyType, shapeId = shapeId)

        val extendsShapeReference = staticReference("Extends")
        val shape = shapeType(fields = listOf(firstField))
        val node = shape(
            extends = listOf(extendsShapeReference),
            fields = listOf(shapeField(shape = baseReference, name = "a", type = stringTypeReference))
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            extendsShapeReference to metaType(shape),
            stringTypeReference to StringMetaType,
            baseReference to metaType(base)
        ))
        typeCheckModuleStatement(node, typeContext)
        typeContext.undefer()
        val metaType = typeContext.typeOf(node)
        assertThat(metaType, isMetaType(isShapeType()))
        val type = metaTypeToType(metaType)!!
        assertThat(type, isShapeType(fields = isSequence(
            isField(name = isIdentifier("a"), type = isStringType, shapeId = equalTo(shapeId))
        ))
        )
    }

    @Test
    fun shapeCannotOverrideFieldWithSuperType() {
        val shapeId = freshTypeId()
        val baseReference = staticReference("Base")
        val base = shapeType(shapeId = shapeId)
        val anyTypeReference = staticReference("Any")

        val firstField = field(name = "a", type = StringType, shapeId = shapeId)

        val extendsShapeReference = staticReference("Extends")
        val shape = shapeType(name = "Extends", fields = listOf(firstField))
        val node = shape(
            extends = listOf(extendsShapeReference),
            fields = listOf(shapeField(shape = baseReference, name = "a", type = anyTypeReference))
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            extendsShapeReference to metaType(shape),
            anyTypeReference to AnyMetaType,
            baseReference to metaType(base)
        ))
        assertThat(
            {
                typeCheckModuleStatement(node, typeContext)
                typeContext.undefer()
            },
            throws(allOf(
                has(FieldDeclarationOverrideTypeConflictError::name, isIdentifier("a")),
                has(FieldDeclarationOverrideTypeConflictError::overrideType, isAnyType),
                has(FieldDeclarationOverrideTypeConflictError::parentShape, isIdentifier("Extends")),
                has(FieldDeclarationOverrideTypeConflictError::parentType, isStringType)
            ))
        )
    }

    @Test
    fun shapeCannotOverrideConstantField() {
        val shapeId = freshTypeId()
        val baseReference = staticReference("Base")
        val base = shapeType(shapeId = shapeId)
        val stringTypeReference = staticReference("String")

        val firstField = field(name = "a", type = StringType, shapeId = shapeId, isConstant = true)

        val extendsShapeReference = staticReference("Extends")
        val shape = shapeType(name = "Extends", fields = listOf(firstField))
        val node = shape(
            extends = listOf(extendsShapeReference),
            fields = listOf(shapeField(shape = baseReference, name = "a", type = stringTypeReference))
        )
        val typeContext = typeContext(referenceTypes = mapOf(
            extendsShapeReference to metaType(shape),
            stringTypeReference to StringMetaType,
            baseReference to metaType(base)
        ))
        assertThat(
            {
                typeCheckModuleStatement(node, typeContext)
                typeContext.undefer()
            },
            throws(allOf(
                has(FieldDeclarationValueConflictError::name, isIdentifier("a")),
                has(FieldDeclarationValueConflictError::parentShape, isIdentifier("Extends"))
            ))
        )
    }

    @Test
    fun shapeWithTypeParametersDeclaresTypeFunction() {
        val typeParameterDeclaration = typeParameter("T")
        val typeParameterReference = staticReference("T")
        val node = shape(
            "X",
            staticParameters = listOf(typeParameterDeclaration),
            fields = listOf(
                shapeField("a", typeParameterReference)
            )
        )

        val typeContext = typeContext(
            references = mapOf(typeParameterReference to typeParameterDeclaration)
        )
        typeCheckModuleStatement(node, typeContext)
        assertThat(typeContext.typeOf(node), isStaticValueType(isParameterizedStaticValue(
            parameters = isSequence(isTypeParameter(name = isIdentifier("T"), variance = isInvariant)),
            value = isShapeType(
                name = isIdentifier("X"),
                fields = isSequence(
                    isField(name = isIdentifier("a"), type = isTypeParameter(name = isIdentifier("T"), variance = isInvariant))
                )
            )
        )))
    }

    @Test
    fun typeParameterHasParsedVariance() {
        val typeParameterDeclaration = typeParameter("T", variance = Variance.COVARIANT)
        val typeParameterReference = staticReference("T")
        val node = shape(
            "X",
            staticParameters = listOf(typeParameterDeclaration)
        )

        val typeContext = typeContext(
            references = mapOf(typeParameterReference to typeParameterDeclaration)
        )
        typeCheckModuleStatement(node, typeContext)
        assertThat(typeContext.typeOf(node), isStaticValueType(isParameterizedStaticValue(
            parameters = isSequence(isTypeParameter(name = isIdentifier("T"), variance = isCovariant))
        )))
    }

    @Test
    fun typeOfShapeIsValidated() {
        val typeParameterDeclaration = typeParameter("T", variance = Variance.CONTRAVARIANT)
        val typeParameterReference = staticReference("T")
        val unitReference = staticReference("Unit")
        val node = shape(
            "Box",
            staticParameters = listOf(typeParameterDeclaration),
            fields = listOf(
                shapeField(type = typeParameterReference)
            )
        )

        val typeContext = typeContext(
            references = mapOf(typeParameterReference to typeParameterDeclaration),
            referenceTypes = mapOf(unitReference to UnitMetaType)
        )
        // TODO: use more specific exception
        assertThat(
            { typeCheckModuleStatement(node, typeContext); typeContext.undefer() },
            throws(has(TypeCheckError::message, equalTo("field type cannot be contravariant")))
        )
    }
}
