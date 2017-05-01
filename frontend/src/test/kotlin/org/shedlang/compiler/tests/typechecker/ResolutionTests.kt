package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ImportPath
import org.shedlang.compiler.ast.VariableReferenceNode
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.*
import java.util.*

class ResolutionTests {
    @Test
    fun variableReferencesAreResolved() {
        val node = variableReference("x")
        val references = resolve(node, globals = mapOf("x" to 42))
        assertThat(references[node], equalTo(42))
    }

    @Test
    fun whenVariableIsUninitialisedThenExceptionIsThrown() {
        val node = variableReference("x")
        val context = resolutionContext(mapOf("x" to 42))

        assertThat(
            { resolve(node, context) },
            throws(has(UninitialisedVariableError::name, equalTo("x")))
        )
    }

    @Test
    fun exceptionWhenVariableNotInScope() {
        val node = variableReference("x")
        val context = resolutionContext()

        assertThat(
            { resolve(node, context) },
            throws(has(UnresolvedReferenceError::name, equalTo("x")))
        )
    }

    @Test
    fun typeReferencesAreResolved() {
        val node = typeReference("X")
        val references = resolve(node, globals = mapOf("X" to 42))
        assertThat(references[node], equalTo(42))
    }

    @Test
    fun exceptionWhenTypeVariableNotInScope() {
        val node = typeReference("X")
        val context = resolutionContext()

        assertThat(
            { resolve(node, context) },
            throws(has(UnresolvedReferenceError::name, equalTo("X")))
        )
    }

    @Test
    fun childrenAreResolved() {
        val node = VariableReferenceNode("x", anySource())
        val references = resolve(expressionStatement(node), globals = mapOf("x" to 42))
        assertThat(references[node], equalTo(42))
    }

    @Test
    fun functionArgumentsAreAddedToScope() {
        val reference = variableReference("x")
        val argument = argument(name = "x", type = typeReference("Int"))
        val node = function(
            arguments = listOf(argument),
            returnType = typeReference("Int"),
            body = listOf(returns(reference))
        )

        val references = resolve(node, globals = mapOf("Int" to -1))

        assertThat(references[reference], equalTo(argument.nodeId))
    }

    @Test
    fun functionArgumentsCanShadowExistingVariables() {
        val reference = variableReference("x")
        val argument = argument(name = "x", type = typeReference("Int"))
        val node = function(
            arguments = listOf(argument),
            returnType = typeReference("Int"),
            body = listOf(returns(reference))
        )

        val references = resolve(node, globals = mapOf(
            reference.name to freshNodeId(),
            "Int" to -1
        ))

        assertThat(references[reference], equalTo(argument.nodeId))
    }

    @Test
    fun functionTypeParametersAreAddedToScope() {
        val reference = typeReference("T")
        val typeParameter = typeParameter("T")
        val node = function(
            typeParameters = listOf(typeParameter),
            arguments = listOf(argument(type = reference)),
            returnType = typeReference("T"),
            body = listOf()
        )

        val references = resolve(node, globals = mapOf())

        assertThat(references[reference], equalTo(typeParameter.nodeId))
    }

    @Test
    fun functionEffectsAreResolved() {
        val effect = variableReference("!io")
        val node = function(
            effects = listOf(effect),
            returnType = typeReference("Int"),
            body = listOf()
        )

        val effectNodeId = freshNodeId()

        val references = resolve(node, globals = mapOf(
            "Int" to freshNodeId(),
            "!io" to effectNodeId
        ))

        assertThat(references[effect], equalTo(effectNodeId))
    }

    @Test
    fun valIntroducesVariableToFunctionScope() {
        val reference = variableReference("x")
        val valStatement = valStatement(name = "x", expression = literalInt())
        val node = function(
            arguments = listOf(),
            returnType = typeReference("Int"),
            body = listOf(
                valStatement,
                returns(reference)
            )
        )

        val references = resolve(node, globals = mapOf("Int" to -1))
        assertThat(references[reference], equalTo(valStatement.nodeId))
    }

    @Test
    fun valExpressionIsResolved() {
        val reference = variableReference("x")
        val valStatement = valStatement(name = "y", expression = reference)

        val references = resolve(valStatement, globals = mapOf("x" to -1))
        assertThat(references[reference], equalTo(-1))
    }

    @Test
    fun importIntroducesVariable() {
        val import = import(path = ImportPath.absolute(listOf("x", "y", "z")))
        val reference = variableReference("z")
        val module = module(
            imports = listOf(import),
            body = listOf(
                function(
                    body = listOf(expressionStatement(reference)),
                    returnType = typeReference("Unit")
                )
            )
        )

        val references = resolve(module, globals = mapOf("Unit" to freshNodeId()))

        assertThat(references[reference], equalTo(import.nodeId))
    }

    @Test
    fun functionsCanCallEachOtherRecursively() {
        val referenceToSecond = variableReference("g")
        val definitionOfFirst = function(name = "f", body = listOf(
            expressionStatement(call(referenceToSecond, listOf()))
        ))
        val referenceToFirst = variableReference("f")
        val definitionOfSecond = function(name = "g", body = listOf(
            expressionStatement(call(referenceToFirst, listOf()))
        ))
        val node = module(body = listOf(
            definitionOfFirst,
            definitionOfSecond
        ))

        val references = resolve(node, globals = mapOf("Unit" to -1))

        assertThat(references[referenceToFirst], equalTo(definitionOfFirst.nodeId))
        assertThat(references[referenceToSecond], equalTo(definitionOfSecond.nodeId))
    }

    @Test
    fun conditionOfIfStatementIsResolved() {
        val reference = variableReference("x")
        val node = ifStatement(condition = reference)

        val references = resolve(node, globals = mapOf("x" to 42))

        assertThat(references[reference], equalTo(42))
    }

    @Test
    fun ifStatementIntroducesScopes() {
        val trueVal = valStatement(name = "x", expression = literalInt())
        val trueReference = variableReference("x")
        val falseVal = valStatement(name = "x", expression = literalInt())
        val falseReference = variableReference("x")

        val node = ifStatement(
            condition = literalBool(true),
            trueBranch = listOf(
                trueVal,
                expressionStatement(trueReference)
            ),
            falseBranch = listOf(
                falseVal,
                expressionStatement(falseReference)
            )
        )

        val references = resolve(node, globals = mapOf())

        assertThat(references[trueReference], equalTo(trueVal.nodeId))
        assertThat(references[falseReference], equalTo(falseVal.nodeId))
    }

    @Test
    fun whenSameNameIsIntroducedTwiceInSameScopeThenErrorIsThrown() {
        val node = module(body = listOf(
            function(name = "f", body = listOf(
                valStatement(name = "x"),
                valStatement(name = "x")
            ))
        ))

        assertThat(
            { resolve(node, globals = mapOf("Unit" to -1)) },
            throws(has(RedeclarationError::name, equalTo("x")))
        )
    }

    @Test
    fun shapeCanBeDeclaredAfterBeingUsedInFunctionSignature() {
        val shapeReference = typeReference("X")
        val shape = shape(name = "X")

        val node = module(body = listOf(
            function(returnType = shapeReference),
            shape
        ))

        val references = resolve(node, globals = mapOf())

        assertThat(references[shapeReference], equalTo(shape.nodeId))
    }

    @Test
    fun shapeCanBeDeclaredAfterBeingUsedInShapeDefinition() {
        val shapeReference = typeReference("X")
        val shape = shape(name = "X")

        val node = module(body = listOf(
            shape(name = "Y", fields = listOf(
                shapeField(name = "a", type = shapeReference)
            )),
            shape
        ))

        val references = resolve(node, globals = mapOf())

        assertThat(references[shapeReference], equalTo(shape.nodeId))
    }

    @Test
    fun unionCanReferenceTypeDefinedLater() {
        val shapeReference = typeReference("X")
        val shape = shape(name = "X")

        val node = module(body = listOf(
            union("Y", listOf(shapeReference)),
            shape
        ))

        val references = resolve(node, globals = mapOf())

        assertThat(references[shapeReference], equalTo(shape.nodeId))
    }

    private fun resolutionContext(
        bindings: Map<String, Int> = mapOf(),
        isInitialised: Set<Int> = setOf(),
        globals: Map<String, Int> = mapOf()
    ) = ResolutionContext(
        bindings = globals + bindings,
        nodes = mutableMapOf(),
        isInitialised = HashSet(globals.values + isInitialised),
        deferred = mutableMapOf()
    )
}
