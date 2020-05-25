package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.types.EmptyEffect
import org.shedlang.compiler.types.IoEffect
import org.shedlang.compiler.types.effectUnion

class EffectUnionTests {
    @Test
    fun whenLeftEffectIsSubEffectOfRightEffectThenUnionIsRightEffect() {
        val union = effectUnion(EmptyEffect, IoEffect)

        assertThat(union, isIoEffect)
    }

    @Test
    fun whenRightEffectIsSubEffectOfLeftEffectThenUnionIsLeftEffect() {
        val union = effectUnion(IoEffect, EmptyEffect)

        assertThat(union, isIoEffect)
    }

    @Test
    fun unrelatedEffectsAreUnioned() {
        val left = opaqueEffect(name = "A")
        val right = opaqueEffect(name = "B")

        val union = effectUnion(left, right)

        assertThat(union, isEffectUnion(members = isSequence(isEffect(left), isEffect(right))))
    }

    @Test
    fun whenLeftEffectIsUnionThenUnionIsFlattened() {
        val effect1 = opaqueEffect(name = "A")
        val effect2 = opaqueEffect(name = "B")
        val effect3 = opaqueEffect(name = "C")

        val union = effectUnion(effectUnion(effect1, effect2), effect3)

        assertThat(union, isEffectUnion(
            members = isSequence(isEffect(effect1), isEffect(effect2), isEffect(effect3))
        ))
    }

    @Test
    fun whenRightEffectIsUnionThenUnionIsFlattened() {
        val effect1 = opaqueEffect(name = "A")
        val effect2 = opaqueEffect(name = "B")
        val effect3 = opaqueEffect(name = "C")

        val union = effectUnion(effect1, effectUnion(effect2, effect3))

        assertThat(union, isEffectUnion(
            members = isSequence(isEffect(effect1), isEffect(effect2), isEffect(effect3))
        ))
    }
}
