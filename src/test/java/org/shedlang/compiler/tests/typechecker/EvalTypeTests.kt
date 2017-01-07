package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.TypeReferenceNode
import org.shedlang.compiler.typechecker.*

class EvalTypeTests {
    @Test
    fun whenTypeReferenceIsForUnboundVariableThenErrorIsThrown() {
        assertThat(
            { evalType(TypeReferenceNode("Int", anySourceLocation()), emptyTypeContext()) },
            throws(has(UnresolvedReferenceError::name, equalTo("Int")))
        )
    }

    @Test
    fun whenReferencedVariableIsNotATypeThenErrorIsThrown() {
        assertThat(
            { evalType(
                TypeReferenceNode("x", anySourceLocation()),
                typeContext(variables = mapOf(Pair("x", IntType)))
            ) },
            // TODO: should be more like MetaType(Hole)
            throwsUnexpectedType(
                expected = isMetaType(AnyType),
                actual = IntType
            )
        )
    }

    private fun isMetaType(type: Type): Matcher<Type> {
        return cast(
            has(MetaType::type, equalTo(type))
        )
    }
}
