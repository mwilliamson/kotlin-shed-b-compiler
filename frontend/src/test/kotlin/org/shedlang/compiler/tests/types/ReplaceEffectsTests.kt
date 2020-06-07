package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.sameInstance
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.tests.computationalEffect
import org.shedlang.compiler.tests.isEffectUnion
import org.shedlang.compiler.tests.isSequence
import org.shedlang.compiler.types.*

class ReplaceEffectsTests {
    @Test
    fun whenEffectParameterHasBindingThenEffectParameterIsReplaced() {
        val effectParameterA = effectParameter(name = "A")
        val effectParameterB = effectParameter(name = "B")
        val boundEffectA = computationalEffect(Identifier("BoundA"))
        val boundEffectB = computationalEffect(Identifier("BoundB"))

        val bindings = mapOf<StaticParameter, StaticValue>(
            effectParameterA to boundEffectA,
            effectParameterB to boundEffectB
        )
        val newEffect = replaceEffects(effectParameterA, bindings)

        assertThat(newEffect, cast(sameInstance(boundEffectA)))
    }

    @Test
    fun whenEffectParameterHasNoBindingThenEffectParameterIsUnchanged() {
        val effectParameterA = effectParameter(name = "A")
        val effectParameterB = effectParameter(name = "B")
        val boundEffectB = computationalEffect(Identifier("BoundB"))

        val bindings = mapOf<StaticParameter, StaticValue>(
            effectParameterB to boundEffectB
        )
        val newEffect = replaceEffects(effectParameterA, bindings)

        assertThat(newEffect, cast(sameInstance(effectParameterA)))
    }

    @Test
    fun membersOfEffectUnionAreReplaced() {
        val effectParameterA = effectParameter(name = "A")
        val effectParameterB = effectParameter(name = "B")
        val boundEffectA = computationalEffect(Identifier("BoundA"))
        val boundEffectB = computationalEffect(Identifier("BoundB"))

        val bindings = mapOf<StaticParameter, StaticValue>(
            effectParameterA to boundEffectA,
            effectParameterB to boundEffectB
        )
        val newEffect = replaceEffects(effectUnion(effectParameterA, effectParameterB), bindings)

        assertThat(newEffect, isEffectUnion(members = isSequence(
            cast(sameInstance(boundEffectA)),
            cast(sameInstance(boundEffectB))
        )))
    }

    @Test
    fun whenReplacingMembersOfUnionThenDuplicateMembersAreCollapsed() {
        val effectParameterA = effectParameter(name = "A")
        val effectParameterB = effectParameter(name = "B")
        val boundEffectA = computationalEffect(Identifier("BoundA"))

        val bindings = mapOf<StaticParameter, StaticValue>(
            effectParameterA to boundEffectA,
            effectParameterB to boundEffectA
        )
        val newEffect = replaceEffects(effectUnion(effectParameterA, effectParameterB), bindings)

        assertThat(newEffect, cast(sameInstance(boundEffectA)))
    }
}
