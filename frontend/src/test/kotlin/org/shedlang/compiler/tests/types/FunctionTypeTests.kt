package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.types.*

class FunctionTypeTests {
    @Test
    fun shortDescriptionForFunctionWithNoArguments() {
        val functionType = functionType(returns = UnitType)
        assertThat(functionType.shortDescription, equalTo("() -> Unit"))
    }

    @Test
    fun shortDescriptionIncludesPositionalArgumentsBetweenParens() {
        val functionType = functionType(
            positionalArguments = listOf(IntType, BoolType),
            returns = UnitType
        )
        assertThat(functionType.shortDescription, equalTo("(Int, Bool) -> Unit"))
    }

    @Test
    fun shortDescriptionIncludesNamedArgumentsAfterPositionalArguments() {
        val functionType = functionType(
            positionalArguments = listOf(IntType),
            namedArguments = mapOf("x" to BoolType),
            returns = UnitType
        )
        assertThat(functionType.shortDescription, equalTo("(Int, x: Bool) -> Unit"))
    }

    @Test
    fun namedArgumentsAreInAlphabeticalOrder() {
        val functionType = functionType(
            positionalArguments = listOf(),
            namedArguments = mapOf("x" to BoolType, "a" to IntType),
            returns = UnitType
        )
        assertThat(functionType.shortDescription, equalTo("(a: Int, x: Bool) -> Unit"))
    }

    @Test
    fun typeParametersAreIncludedInSquareBrackets() {
        val functionType = functionType(
            typeParameters = listOf(
                invariantTypeParameter("T"),
                invariantTypeParameter("U")
            ),
            returns = UnitType
        )
        assertThat(functionType.shortDescription, equalTo("[T, U]() -> Unit"))
    }

    @Test
    fun effectsAreIncludedInShortDescriptionInAlphabeticalOrder() {
        val readEffect = SimpleEffect("!read")
        val writeEffect = SimpleEffect("!write")
        val functionType = functionType(
            effects = setOf(writeEffect, readEffect),
            returns = UnitType
        )
        assertThat(functionType.shortDescription, equalTo("() !read, !write -> Unit"))
    }

    private class SimpleEffect(private val name: String): Effect {
        override val shortDescription: String
            get() = name
    }
}