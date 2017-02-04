package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.nextId
import org.shedlang.compiler.tests.typeReference
import org.shedlang.compiler.typechecker.*

class EvalTypeTests {
    @Test
    fun whenReferencedVariableIsNotATypeThenErrorIsThrown() {
        val reference = typeReference("x")

        assertThat(
            { evalType(
                reference,
                typeContext(referenceTypes = mapOf(reference to IntType))
            ) },
            // TODO: should be more like MetaType(Hole)
            throwsUnexpectedType(
                expected = isMetaType(AnyType),
                actual = IntType
            )
        )
    }

    @Test
    fun whenVariableHasNoTypeThenCompilerErrorIsThrown() {
        val reference = typeReference("x")

        assertThat(
            { evalType(
                reference,
                TypeContext(
                    returnType = null,
                    variables = mutableMapOf(),
                    variableReferences = VariableReferencesMap(mapOf(reference.nodeId to nextId()))
                )
            ) },
            throwsCompilerError("type of x is unknown")
        )
    }

    @Test
    fun whenVariableIsUnresolvedThenCompilerErrorIsThrown() {
        val reference = typeReference("x")

        assertThat(
            { evalType(
                reference,
                typeContext()
            ) },
            throwsCompilerError("reference x is unresolved")
        )
    }

    private fun isMetaType(type: Type): Matcher<Type> {
        return cast(
            has(MetaType::type, equalTo(type))
        )
    }
}
