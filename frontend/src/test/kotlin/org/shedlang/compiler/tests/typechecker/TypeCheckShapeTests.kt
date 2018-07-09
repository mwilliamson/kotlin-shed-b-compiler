package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.frontend.tests.*
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.FieldAlreadyDeclaredError
import org.shedlang.compiler.typechecker.FieldDefinitionConflict
import org.shedlang.compiler.typechecker.TypeCheckError
import org.shedlang.compiler.typechecker.typeCheck
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
            intType to MetaType(IntType),
            boolType to MetaType(BoolType)
        ))
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isShapeType(
            name = isIdentifier("X"),
            fields = isSequence(
                isField(name = isIdentifier("a"), type = isIntType, isConstant = equalTo(false)),
                isField(name = isIdentifier("b"), type = isBoolType, isConstant = equalTo(true))
            )
        )))
    }

    @Test
    fun whenFieldValueDisagreesWithTypeThenErrorIsThrown() {
        val intType = staticReference("Int")
        val node = shape("X", fields = listOf(
            shapeField("a", intType, value = literalBool())
        ))

        val typeContext = typeContext(referenceTypes = mapOf(
            intType to MetaType(IntType)
        ))
        assertThat(
            {
                typeCheck(node, typeContext)
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
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isShapeType(
            name = isIdentifier("X"),
            fields = isSequence(
                isField(name = isIdentifier("a"), type = isBoolType, isConstant = equalTo(true))
            )
        )))
    }

    @Test
    fun whenShapeDeclaresMultipleFieldsWithSameNameThenExceptionIsThrown() {
        val intType = staticReference("Int")
        val node = shape("X", fields = listOf(
            shapeField("a", intType),
            shapeField("a", intType)
        ))

        val typeContext = typeContext(referenceTypes = mapOf(
            intType to MetaType(IntType)
        ))

        assertThat(
            { typeCheck(node, typeContext) },
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

        val shape1Id = freshShapeId()
        val shape1 = shapeType(
            fields = listOf(
                field(name = "b", type = BoolType, shapeId = shape1Id, isConstant = true)
            )
        )

        val shape2Id = freshShapeId()
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
            intType to MetaType(IntType),
            extendsShape1Reference to MetaType(shape1),
            extendsShape2Reference to MetaType(shape2)
        ))
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isShapeType(
            fields = isSequence(
                isField(shapeId = equalTo(shape1Id), name = isIdentifier("b"), type = isBoolType, isConstant = equalTo(true)),
                isField(shapeId = equalTo(shape2Id), name = isIdentifier("c"), type = isStringType, isConstant = equalTo(false)),
                isField(name = isIdentifier("a"), type = isIntType)
            )
        )))
    }

    @Test
    fun whenFieldsWithSameNameHaveDifferentShapeIdsThenErrorIsThrown() {
        val extendsShape1Reference = staticReference("Extends1")
        val extendsShape2Reference = staticReference("Extends2")

        val shape1Id = freshShapeId()
        val shape1 = shapeType(
            fields = listOf(
                field(name = "a", type = BoolType, shapeId = shape1Id, isConstant = true)
            )
        )

        val shape2Id = freshShapeId()
        val shape2 = shapeType(
            fields = listOf(
                field(name = "a", type = StringType, shapeId = shape2Id, isConstant = false)
            )
        )

        val node = shape(
            extends = listOf(
                extendsShape1Reference,
                extendsShape2Reference
            ),
            fields = listOf()
        )

        val typeContext = typeContext(referenceTypes = mapOf(
            extendsShape1Reference to MetaType(shape1),
            extendsShape2Reference to MetaType(shape2)
        ))
        assertThat(
            {
                typeCheck(node, typeContext)
                typeContext.undefer()
            },
            throws(
                has(FieldDefinitionConflict::name, isIdentifier("a"))
            )
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
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isTypeFunction(
            parameters = isSequence(isTypeParameter(name = isIdentifier("T"), variance = isInvariant)),
            type = isShapeType(
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
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isTypeFunction(
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
            referenceTypes = mapOf(unitReference to MetaType(UnitType))
        )
        // TODO: use more specific exception
        assertThat(
            { typeCheck(node, typeContext); typeContext.undefer() },
            throws(has(TypeCheckError::message, equalTo("field type cannot be contravariant")))
        )
    }
}
