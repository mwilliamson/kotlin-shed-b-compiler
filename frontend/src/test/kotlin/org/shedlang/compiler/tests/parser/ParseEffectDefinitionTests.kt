package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.allOf
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.EffectDefinitionNode
import org.shedlang.compiler.parser.parseModuleStatement
import org.shedlang.compiler.tests.isIdentifier
import org.shedlang.compiler.tests.isPair
import org.shedlang.compiler.tests.isSequence

class ParseEffectDefinitionTests {
    @Test
    fun canParseEffectDefinitionWithOneEffect() {
        val source = "effect Try { .throw: (String) -> Nothing }"

        val definition = parseString(::parseModuleStatement, source)

        assertThat(definition, cast(allOf(
            has(EffectDefinitionNode::name, isIdentifier("Try")),
            has(EffectDefinitionNode::operations, isSequence(
                isPair(
                    isIdentifier("throw"),
                    isFunctionType(
                        positionalParameters = isSequence(isStaticReference("String")),
                        returnType = isStaticReference("Nothing")
                    )
                )
            ))
        )))
    }
}
