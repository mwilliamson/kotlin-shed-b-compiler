package org.shedlang.compiler.tests.parser

import com.natpryce.hamkrest.allOf
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.EffectDefinitionNode
import org.shedlang.compiler.ast.OperationDefinitionNode
import org.shedlang.compiler.frontend.parser.parseFunctionStatement
import org.shedlang.compiler.frontend.parser.parseModuleStatement
import org.shedlang.compiler.tests.isIdentifier
import org.shedlang.compiler.tests.isSequence

class ParseEffectDefinitionTests {
    @Test
    fun canParseEffectDefinitionWithOneOperation() {
        val source = "effect Try { .throw: (String) -> Nothing }"

        val definition = parseString(::parseModuleStatement, source)

        assertThat(definition, cast(allOf(
            has(EffectDefinitionNode::name, isIdentifier("Try")),
            has(EffectDefinitionNode::operations, isSequence(
                allOf(
                    has(OperationDefinitionNode::name, isIdentifier("throw")),
                    has(OperationDefinitionNode::type, isFunctionTypeNode(
                        positionalParameters = isSequence(isTypeLevelReferenceNode("String")),
                        returnType = isTypeLevelReferenceNode("Nothing")
                    ))
                )
            ))
        )))
    }

    @Test
    fun operationsAreOrderedByName() {
        val source = "effect Eff { .b: () -> Unit, .a: () -> Unit }"

        val definition = parseString(::parseModuleStatement, source)

        assertThat(definition, cast(allOf(
            has(EffectDefinitionNode::operations, isSequence(
                has(OperationDefinitionNode::name, isIdentifier("a")),
                has(OperationDefinitionNode::name, isIdentifier("b"))
            ))
        )))
    }

    @Test
    fun canParseEffectDefinitionAsModuleStatement() {
        val source = "effect Try { .throw: (String) -> Nothing }"

        val definition = parseString(::parseModuleStatement, source)

        assertThat(definition, cast(allOf(
            has(EffectDefinitionNode::name, isIdentifier("Try")),
        )))
    }

    @Test
    fun canParseEffectDefinitionAsFunctionStatement() {
        val source = "effect Try { .throw: (String) -> Nothing }"

        val definition = parseString(::parseFunctionStatement, source)

        assertThat(definition, cast(allOf(
            has(EffectDefinitionNode::name, isIdentifier("Try")),
        )))
    }
}
