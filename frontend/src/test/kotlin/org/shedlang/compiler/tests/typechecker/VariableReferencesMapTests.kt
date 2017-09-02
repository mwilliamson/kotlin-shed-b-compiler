package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.staticReference
import org.shedlang.compiler.typechecker.ResolvedReferencesMap

class VariableReferencesMapTests {
    @Test
    fun whenVariableIsUnresolvedThenCompilerErrorIsThrown() {
        val reference = staticReference("x")
        val references = ResolvedReferencesMap(mapOf())

        assertThat(
            { references[reference] },
            throwsCompilerError("reference x is unresolved")
        )
    }
}
