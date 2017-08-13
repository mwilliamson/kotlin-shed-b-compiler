package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.FieldAlreadyDeclaredError
import org.shedlang.compiler.typechecker.TypeCheckError
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.*

class TypeCheckShapeTests {
    @Test
    fun shapeDeclaresType() {
        val intType = typeReference("Int")
        val boolType = typeReference("Bool")
        val node = shape("X", fields = listOf(
            shapeField("a", intType),
            shapeField("b", boolType)
        ))

        val typeContext = typeContext(referenceTypes = mapOf(
            intType to MetaType(IntType),
            boolType to MetaType(BoolType)
        ))
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isShapeType(
            name = equalTo("X"),
            fields = listOf("a" to isIntType, "b" to isBoolType)
        )))
    }

    @Test
    fun whenShapeDeclaresMultipleFieldsWithSameNameThenExceptionIsThrown() {
        val intType = typeReference("Int")
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
                has(FieldAlreadyDeclaredError::fieldName, equalTo("a"))
            )
        )
    }

    @Test
    fun shapeWithTypeParametersDeclaresTypeFunction() {
        val typeParameterDeclaration = typeParameter("T")
        val typeParameterReference = typeReference("T")
        val node = shape(
            "X",
            typeParameters = listOf(typeParameterDeclaration),
            fields = listOf(
                shapeField("a", typeParameterReference)
            )
        )

        val typeContext = typeContext(
            references = mapOf(typeParameterReference to typeParameterDeclaration)
        )
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isTypeFunction(
            parameters = isSequence(isTypeParameter(name = equalTo("T"), variance = isInvariant)),
            type = isShapeType(
                name = equalTo("X"),
                fields = listOf("a" to isTypeParameter(name = equalTo("T"), variance = isInvariant))
            )
        )))
    }

    @Test
    fun typeParameterHasParsedVariance() {
        val typeParameterDeclaration = typeParameter("T", variance = Variance.COVARIANT)
        val typeParameterReference = typeReference("T")
        val node = shape(
            "X",
            typeParameters = listOf(typeParameterDeclaration)
        )

        val typeContext = typeContext(
            references = mapOf(typeParameterReference to typeParameterDeclaration)
        )
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isTypeFunction(
            parameters = isSequence(isTypeParameter(name = equalTo("T"), variance = isCovariant))
        )))
    }

    @Test
    @Disabled("WIP")
    fun whenCovariantTypeParameterIsInContravariantPositionThenErrorIsThrown() {
        val typeParameterDeclaration = typeParameter("T", variance = Variance.COVARIANT)
        val typeParameterReference = typeReference("T")
        val unitReference = typeReference("Unit")
        val node = shape(
            "Matcher",
            typeParameters = listOf(typeParameterDeclaration),
            fields = listOf(
                shapeField(type = functionType(arguments = listOf(typeParameterReference), returnType = unitReference))
            )
        )

        val typeContext = typeContext(
            references = mapOf(typeParameterReference to typeParameterDeclaration),
            referenceTypes = mapOf(unitReference to MetaType(UnitType))
        )
        assertThat(
            { typeCheck(node, typeContext) },
            throws<TypeCheckError>()
        )
    }

    @Test
    @Disabled
    fun whenContravariantTypeParameterIsInCovariantPositionThenErrorIsThrown() {
        val typeParameterDeclaration = typeParameter("T", variance = Variance.CONTRAVARIANT)
        val typeParameterReference = typeReference("T")
        val unitReference = typeReference("Unit")
        val node = shape(
            "Box",
            typeParameters = listOf(typeParameterDeclaration),
            fields = listOf(
                shapeField(type = typeParameterReference)
            )
        )

        val typeContext = typeContext(
            references = mapOf(typeParameterReference to typeParameterDeclaration),
            referenceTypes = mapOf(unitReference to MetaType(UnitType))
        )
        assertThat(
            { typeCheck(node, typeContext) },
            throws<TypeCheckError>()
        )
    }
}
