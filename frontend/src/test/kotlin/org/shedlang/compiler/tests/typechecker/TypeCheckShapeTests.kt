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
        val intType = typeLevelReference("Int")
        val boolType = typeLevelReference("Bool")
        val node = shape("X", fields = listOf(
            shapeField("a", intType),
            shapeField("b", boolType)
        ))
        val typeContext = typeContext(
            moduleName = listOf("Example"),
            referenceTypes = mapOf(
                intType to IntMetaType,
                boolType to BoolMetaType
            ),
        )

        typeCheckModuleStatementAllPhases(node, typeContext)

        assertThat(typeContext.typeOf(node), isMetaType(
            isShapeType(
                qualifiedName = equalTo(QualifiedName.topLevelType(listOf("Example"), "X")),
                name = isIdentifier("X"),
                fields = isSequence(
                    isField(name = isIdentifier("a"), type = isIntType),
                    isField(name = isIdentifier("b"), type = isBoolType)
                )
            )
        ))
    }

    @Test
    fun whenShapeDeclaresMultipleFieldsWithSameNameThenExceptionIsThrown() {
        val intType = typeLevelReference("Int")
        val node = shape("X", fields = listOf(
            shapeField("a", intType),
            shapeField("a", intType)
        ))

        val typeContext = typeContext(referenceTypes = mapOf(
            intType to IntMetaType
        ))

        assertThat(
            { typeCheckModuleStatementAllPhases(node, typeContext) },
            throws(
                has(FieldAlreadyDeclaredError::fieldName, isIdentifier("a"))
            )
        )
    }

    @Test
    fun shapeIncludesFieldsFromExtendedShapes() {
        val intType = typeLevelReference("Int")
        val extendsShape1Reference = typeLevelReference("Extends1")
        val extendsShape2Reference = typeLevelReference("Extends2")

        val shape1Id = freshTypeId()
        val shape1 = shapeType(
            fields = listOf(
                field(name = "b", type = BoolType, shapeId = shape1Id)
            )
        )

        val shape2Id = freshTypeId()
        val shape2 = shapeType(
            fields = listOf(
                field(name = "c", type = StringType, shapeId = shape2Id)
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
        typeCheckModuleStatementAllPhases(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(
            isShapeType(
                fields = isSequence(
                    isField(name = isIdentifier("a"), type = isIntType),
                    isField(shapeId = equalTo(shape1Id), name = isIdentifier("b"), type = isBoolType),
                    isField(shapeId = equalTo(shape2Id), name = isIdentifier("c"), type = isStringType)
                )
            )
        ))
    }

    @Test
    fun whenFieldsWithSameNameHaveDifferentShapeIdsThenErrorIsThrown() {
        val shape1Id = freshTypeId()
        val firstField = field(name = "a", type = BoolType, shapeId = shape1Id)

        val shape2Id = freshTypeId()
        val secondField = field(name = "a", type = StringType, shapeId = shape2Id)

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

    private fun mergeFields(first: Field, second: Field): Field {
        val extendsShape1Reference = typeLevelReference("Extends1")
        val extendsShape2Reference = typeLevelReference("Extends2")

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
        typeCheckModuleStatementAllPhases(node, typeContext)

        val metaType = typeContext.typeOf(node)
        assertThat(metaType, isMetaType(isShapeType()))
        val type = metaTypeToType(metaType)
        return (type as SimpleShapeType).fields.values.single()
    }

    @Test
    fun shapeCannotOverrideFieldWithDifferentShape() {
        val shapeId = freshTypeId()
        val stringTypeReference = typeLevelReference("String")

        val firstField = field(name = "a", type = AnyType, shapeId = shapeId)

        val extendsShapeReference = typeLevelReference("Extends")
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
                typeCheckModuleStatementAllPhases(node, typeContext)
            },
            throws(
                has(FieldDeclarationShapeIdConflictError::name, isIdentifier("a"))
            )
        )
    }

    @Test
    fun shapeCanOverrideFieldWithSubtypeUsingSameShape() {
        val shapeId = freshTypeId()
        val baseReference = typeLevelReference("Base")
        val base = shapeType(shapeId = shapeId)
        val stringTypeReference = typeLevelReference("String")

        val firstField = field(name = "a", type = AnyType, shapeId = shapeId)

        val extendsShapeReference = typeLevelReference("Extends")
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
        typeCheckModuleStatementAllPhases(node, typeContext)
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
        val baseReference = typeLevelReference("Base")
        val base = shapeType(shapeId = shapeId)
        val anyTypeReference = typeLevelReference("Any")

        val firstField = field(name = "a", type = StringType, shapeId = shapeId)

        val extendsShapeReference = typeLevelReference("Extends")
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
                typeCheckModuleStatementAllPhases(node, typeContext)
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
    fun shapeWithTypeParametersDeclaresTypeFunction() {
        val typeParameterDeclaration = typeParameter("T")
        val typeParameterReference = typeLevelReference("T")
        val node = shape(
            "X",
            typeLevelParameters = listOf(typeParameterDeclaration),
            fields = listOf(
                shapeField("a", typeParameterReference)
            )
        )

        val typeContext = typeContext(
            references = mapOf(typeParameterReference to typeParameterDeclaration)
        )
        typeCheckModuleStatementAllPhases(node, typeContext)
        assertThat(typeContext.typeOf(node), isTypeLevelValueType(isTypeConstructor(
            parameters = isSequence(isTypeParameter(name = isIdentifier("T"), variance = isInvariant)),
            genericType = isShapeType(
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
        val typeParameterReference = typeLevelReference("T")
        val node = shape(
            "X",
            typeLevelParameters = listOf(typeParameterDeclaration)
        )

        val typeContext = typeContext(
            references = mapOf(typeParameterReference to typeParameterDeclaration)
        )
        typeCheckModuleStatementAllPhases(node, typeContext)
        assertThat(typeContext.typeOf(node), isTypeLevelValueType(isTypeConstructor(
            parameters = isSequence(isTypeParameter(name = isIdentifier("T"), variance = isCovariant))
        )))
    }

    @Test
    fun typeOfShapeIsValidated() {
        val typeParameterDeclaration = typeParameter("T", variance = Variance.CONTRAVARIANT)
        val typeParameterReference = typeLevelReference("T")
        val unitReference = typeLevelReference("Unit")
        val node = shape(
            "Box",
            typeLevelParameters = listOf(typeParameterDeclaration),
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
            { typeCheckModuleStatementAllPhases(node, typeContext) },
            throws(has(TypeCheckError::message, equalTo("field type cannot be contravariant")))
        )
    }
}
