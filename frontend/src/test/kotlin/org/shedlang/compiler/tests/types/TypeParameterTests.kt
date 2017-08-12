package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.types.invariantTypeParameter

class TypeParameterTests {
    @Test
    fun shortDescriptionOfInvariantTypeParameterIsNameOfTypeParameter() {
        val typeParameter = invariantTypeParameter("T")
        assertThat(typeParameter.shortDescription, equalTo("T"))
    }
}