package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.evalTypeLevelValue
import org.shedlang.compiler.types.effectType

class TypeCheckTypeLevelUnionTests {
    @Test
    fun typeLevelUnionOfEffectsIsEvaluatedToEffectUnion() {
        val effectA = userDefinedEffect(Identifier("A"))
        val effectB = userDefinedEffect(Identifier("B"))
        val effectReferenceA = typeLevelReference("A")
        val effectReferenceB = typeLevelReference("B")
        val node = typeLevelUnion(
            elements = listOf(effectReferenceA, effectReferenceB)
        )

        val context = typeContext(
            referenceTypes = mapOf(
                effectReferenceA to effectType(effectA),
                effectReferenceB to effectType(effectB)
            )
        )
        val effect = evalTypeLevelValue(node, context)

        assertThat(effect, isEffectUnion(
            members = isSequence(isEffect(effectA), isEffect(effectB))
        ))
    }
}
