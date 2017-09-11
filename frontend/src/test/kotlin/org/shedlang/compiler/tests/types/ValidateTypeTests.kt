package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.isSequence
import org.shedlang.compiler.tests.shapeType
import org.shedlang.compiler.tests.unionType
import org.shedlang.compiler.types.*

class ValidateTypeTests {
    @Test
    fun positionalArgumentTypesCannotBeCovariant() {
        val type = functionType(positionalArguments = listOf(covariantTypeParameter("T")))
        assertThat(validateType(type = type), isFailure("argument type cannot be covariant"))
    }

    @Test
    fun namedArgumentTypesCannotBeCovariant() {
        val type = functionType(namedArguments = mapOf("x" to covariantTypeParameter("T")))
        assertThat(validateType(type = type), isFailure("argument type cannot be covariant"))
    }

    @Test
    fun returnTypesCannotBeContravariant() {
        val type = functionType(returns = contravariantTypeParameter("T"))
        assertThat(validateType(type = type), isFailure("return type cannot be contravariant"))
    }

    @Test
    fun shapeFieldsCanBeInvariantTypeParameters() {
        val type = shapeType(fields = mapOf("value" to covariantTypeParameter("T")))
        assertThat(validateType(type = type), isSuccess)
    }

    @Test
    fun shapeFieldsCanBeCovariantTypeParameters() {
        val type = shapeType(fields = mapOf("value" to invariantTypeParameter("T")))
        assertThat(validateType(type = type), isSuccess)
    }

    @Test
    fun shapeFieldsCannotBeContravariantTypeParameters() {
        val type = shapeType(fields = mapOf("value" to contravariantTypeParameter("T")))
        assertThat(validateType(type = type), isFailure("field type cannot be contravariant"))
    }

    @Test
    fun whenUnionMembersHaveValueForSameTagThenUnionIsValid() {
        val taggedType = shapeType(tag = Tag("Tagged"))
        val member1 = shapeType("Member1", tagValueFor = taggedType.tag)
        val member2 = shapeType("Member2", tagValueFor = taggedType.tag)
        val type = unionType(members = listOf(member1, member2))
        assertThat(validateType(type = type), isSuccess)
    }

    @Test
    fun whenUnionMembersHaveValueForDifferentTagsThenUnionIsInValid() {
        val taggedType1 = shapeType(tag = Tag("Tagged1"))
        val taggedType2 = shapeType(tag = Tag("Tagged2"))
        val member1 = shapeType("Member1", tagValueFor = taggedType1.tag)
        val member2 = shapeType("Member2", tagValueFor = taggedType2.tag)
        val type = unionType(members = listOf(member1, member2))

        assertThat(validateType(type = type), isFailure("union members must have values for same tag"))
    }

    @Test
    fun whenUnionMembersDontHaveTagValueThenUnionIsInValid() {
        val member = shapeType("Member1", tagValueFor = null)
        val type = unionType(members = listOf(member))

        assertThat(validateType(type = type), isFailure("union members must have tag values"))
    }

    private val isSuccess = equalTo(ValidateTypeResult.success)
    private fun isFailure(vararg errors: String) = has(
        ValidateTypeResult::errors,
        isSequence(*errors.map({ error -> equalTo(error) }).toTypedArray())
    )
}
