package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.typeCheckModuleStatement
import org.shedlang.compiler.types.BottomType
import org.shedlang.compiler.types.StringType
import org.shedlang.compiler.types.metaType

class TypeCheckEffectDefinitionTests {
    @Test
    fun effectDefinitionCreatesUserDefinedEffect() {
        val stringReference = staticReference("String")
        val bottomReference = staticReference("Bottom")
        val effectDefinition = effectDefinition(
            name = "Try",
            operations = listOf(
                operationDefinition(
                    name = "throw",
                    type = functionTypeNode(
                        positionalParameters = listOf(stringReference),
                        returnType = bottomReference
                    )
                )
            )
        )

        val typeContext = typeContext(
            referenceTypes = mapOf(
                stringReference to metaType(StringType),
                bottomReference to metaType(BottomType)
            )
        )
        typeCheckModuleStatement(effectDefinition, typeContext)

        assertThat(typeContext.typeOf(effectDefinition), isEffectType(isUserDefinedEffect(
            name = isIdentifier("Try"),
            operations = isMap(
                Identifier("throw") to isFunctionType(
                    positionalParameters = isSequence(isStringType),
                    effect = isUserDefinedEffect(name = isIdentifier("Try")),
                    returnType = isBottomType
                )
            )
        )))
    }
}
