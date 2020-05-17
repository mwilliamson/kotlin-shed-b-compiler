package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.allOf
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.typeCheckTypesModuleStatement
import org.shedlang.compiler.types.OpaqueEffect

class TypeCheckEffectDeclarationTests {
    @Test
    fun effectDeclarationCreatesNewEffect() {
        val node = effectDeclaration(name = "Write")
        val typeContext = typeContext()

        typeCheckTypesModuleStatement(node, typeContext)
        typeContext.undefer()

        assertThat(typeContext.typeOf(node), isEffectType(
            cast(allOf(
                has(OpaqueEffect::name, isIdentifier("Write")),
                has(OpaqueEffect::arguments, isSequence())
            ))
        ))
    }

    @Test
    fun parametrizedEffectDeclarationCreatesNewParameterizedEffect() {
        val node = effectDeclaration(
            name = "Write",
            staticParameters = listOf(typeParameter("T"))
        )
        val typeContext = typeContext()

        typeCheckTypesModuleStatement(node, typeContext)
        typeContext.undefer()

        assertThat(typeContext.typeOf(node), isStaticValueType(
            isParameterizedStaticValue(
                parameters = isSequence(isTypeParameter(name = isIdentifier("T"))),
                value = cast(allOf(
                    has(OpaqueEffect::name, isIdentifier("Write")),
                    has(OpaqueEffect::arguments, isSequence(isTypeParameter(name = isIdentifier("T"))))
                ))
            )
        ))
    }
}
