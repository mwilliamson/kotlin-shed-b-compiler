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
    fun whenUnionMembersHaveValueForTagOfUnionThenUnionIsValid() {
        val tag = Tag("Tagged")
        val member1 = shapeType("Member1", tagValue = TagValue(tag, 1))
        val member2 = shapeType("Member2", tagValue = TagValue(tag, 2))
        val type = unionType(members = listOf(member1, member2), tag = tag)
        assertThat(validateType(type = type), isSuccess)
    }

    @Test
    fun whenUnionMembersDontHaveTagValueThenUnionIsInvalid() {
        val tag = Tag("Tagged")
        val member = shapeType("Member1", tagValue = null)
        val type = unionType(members = listOf(member), tag = tag)

        assertThat(validateType(type = type), isFailure("union member did not have tag value for Tagged"))
    }

    @Test
    fun whenUnionMembersHaveTagValuesForWrongTagThenUnionIsInvalid() {
        val tag = Tag("Tagged")
        val tag1 = Tag("Tagged1")
        val tag2 = Tag("Tagged2")
        val member1 = shapeType("Member1", tagValue = TagValue(tag1, 1))
        val member2 = shapeType("Member2", tagValue = TagValue(tag2, 2))
        val type = unionType(members = listOf(member1, member2), tag = tag)

        assertThat(validateType(type = type), isFailure("union member did not have tag value for Tagged"))
    }

    private val isSuccess = equalTo(ValidateTypeResult.success)
    private fun isFailure(vararg errors: String) = has(
        ValidateTypeResult::errors,
        isSequence(*errors.map({ error -> equalTo(error) }).toTypedArray())
    )
}
