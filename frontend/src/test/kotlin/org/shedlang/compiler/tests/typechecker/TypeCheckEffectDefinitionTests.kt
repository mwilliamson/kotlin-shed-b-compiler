package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.typeCheckFunctionStatement
import org.shedlang.compiler.typechecker.typeCheckModuleStatement
import org.shedlang.compiler.types.NothingType
import org.shedlang.compiler.types.StringType
import org.shedlang.compiler.types.metaType

class TypeCheckEffectDefinitionTests {
    @Test
    fun canTypeCheckEffectDefinitionAsModuleStatement() {
        val effectDefinition = effectDefinition(
            name = "Try",
            operations = listOf()
        )

        val typeContext = typeContext()
        typeCheckModuleStatement(effectDefinition, typeContext)

        assertThat(typeContext.typeOf(effectDefinition), isEffectType(isUserDefinedEffect(
            name = isIdentifier("Try"),
        )))
    }

    @Test
    fun canTypeCheckEffectDefinitionAsFunctionStatement() {
        val effectDefinition = effectDefinition(
            name = "Try",
            operations = listOf()
        )

        val typeContext = typeContext()
        typeCheckFunctionStatement(effectDefinition, typeContext)

        assertThat(typeContext.typeOf(effectDefinition), isEffectType(isUserDefinedEffect(
            name = isIdentifier("Try"),
        )))
    }

    @Test
    fun effectDefinitionCreatesUserDefinedEffect() {
        val stringReference = typeLevelReference("String")
        val nothingReference = typeLevelReference("Nothing")
        val effectDefinition = effectDefinition(
            name = "Try",
            operations = listOf(
                operationDefinition(
                    name = "throw",
                    type = functionTypeNode(
                        positionalParameters = listOf(stringReference),
                        returnType = nothingReference
                    )
                )
            )
        )

        val typeContext = typeContext(
            referenceTypes = mapOf(
                stringReference to metaType(StringType),
                nothingReference to metaType(NothingType)
            )
        )
        typeCheckModuleStatement(effectDefinition, typeContext)

        assertThat(typeContext.typeOf(effectDefinition), isEffectType(isUserDefinedEffect(
            name = isIdentifier("Try"),
            operations = isMap(
                Identifier("throw") to isFunctionType(
                    positionalParameters = isSequence(isStringType),
                    effect = isUserDefinedEffect(name = isIdentifier("Try")),
                    returnType = isNothingType
                )
            )
        )))
    }
}
