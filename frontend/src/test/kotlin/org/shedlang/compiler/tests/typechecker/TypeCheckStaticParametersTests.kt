package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.present
import org.junit.jupiter.api.Test
import org.shedlang.compiler.frontend.tests.isType
import org.shedlang.compiler.frontend.tests.isTypeParameter
import org.shedlang.compiler.tests.isSequence
import org.shedlang.compiler.tests.staticReference
import org.shedlang.compiler.tests.typeParameter
import org.shedlang.compiler.tests.unionType
import org.shedlang.compiler.typechecker.typeCheckStaticParameters
import org.shedlang.compiler.types.MetaType

class TypeCheckStaticParametersTests {
    @Test
    fun whenNodeHasMemberOfThenStaticParameterHasMemberOf() {
        val unionTypeReference = staticReference("U")
        val unionType = unionType("U")

        val node = typeParameter(memberOf = unionTypeReference)
        val context = typeContext(referenceTypes = mapOf(unionTypeReference to MetaType(unionType)))
        val staticParameters = typeCheckStaticParameters(listOf(node), context)
        assertThat(staticParameters, isSequence(isTypeParameter(
            memberOf = present(isType(unionType))
        )))
    }
}
