package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.tests.field
import org.shedlang.compiler.tests.isSequence
import org.shedlang.compiler.tests.shapeType
import org.shedlang.compiler.types.*

class ValidateTypeLevelValueTests {
    @Test
    fun positionalParameterTypesCannotBeCovariant() {
        val type = functionType(positionalParameters = listOf(covariantTypeParameter("T")))
        assertThat(validateTypeLevelValue(value = type), isFailure("parameter type cannot be covariant"))
    }

    @Test
    fun namedParameterTypesCannotBeCovariant() {
        val type = functionType(namedParameters = mapOf(Identifier("x") to covariantTypeParameter("T")))
        assertThat(validateTypeLevelValue(value = type), isFailure("parameter type cannot be covariant"))
    }

    @Test
    fun returnTypesCannotBeContravariant() {
        val type = functionType(returns = contravariantTypeParameter("T"))
        assertThat(validateTypeLevelValue(value = type), isFailure("return type cannot be contravariant"))
    }

    @Test
    fun shapeFieldsCanBeInvariantTypeParameters() {
        val type = shapeType(fields = listOf(field("value", covariantTypeParameter("T"))))
        assertThat(validateTypeLevelValue(value = type), isSuccess)
    }

    @Test
    fun shapeFieldsCanBeCovariantTypeParameters() {
        val type = shapeType(fields = listOf(field("value", invariantTypeParameter("T"))))
        assertThat(validateTypeLevelValue(value = type), isSuccess)
    }

    @Test
    fun shapeFieldsCannotBeContravariantTypeParameters() {
        val type = shapeType(fields = listOf(field("value", contravariantTypeParameter("T"))))
        assertThat(validateTypeLevelValue(value = type), isFailure("field type cannot be contravariant"))
    }

    private val isSuccess = equalTo(ValidateTypeResult.success)
    private fun isFailure(vararg errors: String) = has(
        ValidateTypeResult::errors,
        isSequence(*errors.map({ error -> equalTo(error) }).toTypedArray())
    )
}
