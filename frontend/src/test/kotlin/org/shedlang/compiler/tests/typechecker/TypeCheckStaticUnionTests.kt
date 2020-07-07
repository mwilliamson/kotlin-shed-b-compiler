package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.evalStaticValue
import org.shedlang.compiler.types.effectType

class TypeCheckStaticUnionTests {
    @Test
    fun staticUnionOfEffectsIsEvaluatedToEffectUnion() {
        val effectA = userDefinedEffect(Identifier("A"))
        val effectB = userDefinedEffect(Identifier("B"))
        val effectReferenceA = staticReference("A")
        val effectReferenceB = staticReference("B")
        val node = staticUnion(
            elements = listOf(effectReferenceA, effectReferenceB)
        )

        val context = typeContext(
            referenceTypes = mapOf(
                effectReferenceA to effectType(effectA),
                effectReferenceB to effectType(effectB)
            )
        )
        val effect = evalStaticValue(node, context)

        assertThat(effect, isEffectUnion(
            members = isSequence(isEffect(effectA), isEffect(effectB))
        ))
    }
}
