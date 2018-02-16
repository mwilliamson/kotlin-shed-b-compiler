package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ImportPath
import org.shedlang.compiler.ast.VariableBindingNode
import org.shedlang.compiler.ast.VariableReferenceNode
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.*
import java.util.*

class ResolutionTests {
    private val declaration = valStatement("declaration")
    private var declarationIndex = 1
    private fun anyDeclaration(): VariableBindingNode {
        return valStatement("declaration-" + declarationIndex++)
    }

    @Test
    fun variableReferencesAreResolved() {
        val node = variableReference("x")
        val references = resolve(node, globals = mapOf("x" to declaration))
        assertThat(references[node], isVariableBinding(declaration))
    }

    @Test
    fun whenVariableIsUninitialisedThenExceptionIsThrown() {
        val node = variableReference("x")
        val context = resolutionContext(mapOf("x" to anyDeclaration()))

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
        val node = staticReference("X")
        val references = resolve(node, globals = mapOf("X" to declaration))
        assertThat(references[node], isVariableBinding(declaration))
    }

    @Test
    fun exceptionWhenTypeVariableNotInScope() {
        val node = staticReference("X")
        val context = resolutionContext()

        assertThat(
            { resolve(node, context) },
            throws(has(UnresolvedReferenceError::name, equalTo("X")))
        )
    }

    @Test
    fun childrenAreResolved() {
        val node = VariableReferenceNode("x", anySource())
        val references = resolve(expressionStatement(node), globals = mapOf("x" to declaration))
        assertThat(references[node], isVariableBinding(declaration))
    }

    @Test
    fun functionDeclarationArgumentsAreAddedToScope() {
        val reference = variableReference("x")
        val parameter = parameter(name = "x", type = staticReference("Int"))
        val node = function(
            parameters = listOf(parameter),
            returnType = staticReference("Int"),
            body = listOf(expressionStatement(reference))
        )

        val references = resolve(node, globals = mapOf("Int" to anyDeclaration()))

        assertThat(references[reference], isVariableBinding(parameter))
    }

    @Test
    fun functionExpressionArgumentsAreAddedToScope() {
        val reference = variableReference("x")
        val parameter = parameter(name = "x", type = staticReference("Int"))
        val node = functionExpression(
            parameters = listOf(parameter),
            returnType = staticReference("Int"),
            body = listOf(expressionStatement(reference))
        )

        val references = resolve(node, globals = mapOf("Int" to anyDeclaration()))

        assertThat(references[reference], isVariableBinding(parameter))
    }

    @Test
    fun functionArgumentsCanShadowExistingVariables() {
        val reference = variableReference("x")
        val parameter = parameter(name = "x", type = staticReference("Int"))
        val node = function(
            parameters = listOf(parameter),
            returnType = staticReference("Int"),
            body = listOf(expressionStatement(reference))
        )

        val references = resolve(node, globals = mapOf(
            reference.name to anyDeclaration(),
            "Int" to anyDeclaration()
        ))

        assertThat(references[reference], isVariableBinding(parameter))
    }

    @Test
    fun staticParametersInFunctionDeclarationAreAddedToScope() {
        val reference = staticReference("T")
        val typeParameter = typeParameter("T")
        val node = function(
            staticParameters = listOf(typeParameter),
            parameters = listOf(parameter(type = reference)),
            returnType = staticReference("T"),
            body = listOf()
        )

        val references = resolve(node, globals = mapOf())

        assertThat(references[reference], isVariableBinding(typeParameter))
    }

    @Test
    fun functionEffectsAreResolved() {
        val effect = staticReference("!Io")
        val node = function(
            effects = listOf(effect),
            returnType = staticReference("Int"),
            body = listOf()
        )

        val references = resolve(node, globals = mapOf(
            "Int" to anyDeclaration(),
            "!Io" to declaration
        ))

        assertThat(references[effect], isVariableBinding(declaration))
    }

    @Test
    fun staticParametersInFunctionTypeAreAddedToScope() {
        val reference = staticReference("T")
        val typeParameter = typeParameter("T")
        val node = functionTypeNode(
            staticParameters = listOf(typeParameter),
            parameters = listOf(reference),
            returnType = staticReference("T")
        )

        val references = resolve(node, globals = mapOf())

        assertThat(references[reference], isVariableBinding(typeParameter))
    }

    @Test
    fun valIntroducesVariableToFunctionScope() {
        val reference = variableReference("x")
        val valStatement = valStatement(name = "x", expression = literalInt())
        val node = function(
            parameters = listOf(),
            returnType = staticReference("Int"),
            body = listOf(
                valStatement,
                expressionStatement(reference)
            )
        )

        val references = resolve(node, globals = mapOf("Int" to anyDeclaration()))
        assertThat(references[reference], isVariableBinding(valStatement))
    }

    @Test
    fun valExpressionIsResolved() {
        val reference = variableReference("x")
        val valStatement = valStatement(name = "y", expression = reference)

        val references = resolve(valStatement, globals = mapOf("x" to declaration))
        assertThat(references[reference], isVariableBinding(declaration))
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
                    returnType = staticReference("Unit")
                )
            )
        )

        val references = resolve(module, globals = mapOf("Unit" to anyDeclaration()))

        assertThat(references[reference], isVariableBinding(import))
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

        val references = resolve(node, globals = mapOf("Unit" to anyDeclaration()))

        assertThat(references[referenceToFirst], isVariableBinding(definitionOfFirst))
        assertThat(references[referenceToSecond], isVariableBinding(definitionOfSecond))
    }

    @Test
    fun valExpressionCannotCallFunctionThatDirectlyUsesVariable() {
        val valDeclaration = valStatement(name = "x", expression = call(variableReference("f")))
        val function = function(
            name = "f",
            returnType = staticReference("Int"),
            body = listOf(expressionStatement(variableReference("x")))
        )

        val node = module(body = listOf(
            function,
            valDeclaration
        ))

        assertThat(
            { resolve(node, globals = mapOf("Int" to anyDeclaration()))},
            throws(has(UninitialisedVariableError::name, equalTo("x")))
        )
    }

    @Test
    fun conditionOfIfStatementIsResolved() {
        val reference = variableReference("x")
        val node = ifStatement(condition = reference)

        val references = resolve(node, globals = mapOf("x" to declaration))

        assertThat(references[reference], isVariableBinding(declaration))
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
            elseBranch = listOf(
                falseVal,
                expressionStatement(falseReference)
            )
        )

        val references = resolve(node, globals = mapOf())

        assertThat(references[trueReference], isVariableBinding(trueVal))
        assertThat(references[falseReference], isVariableBinding(falseVal))
    }

    @Test
    fun expressionOfWhenExpressionIsResolved() {
        val reference = variableReference("x")
        val node = whenExpression(expression = reference)

        val references = resolve(node, globals = mapOf("x" to declaration))

        assertThat(references[reference], isVariableBinding(declaration))
    }

    @Test
    fun typesInWhenBranchesAreResolved() {
        val typeReference = staticReference("T")
        val node = whenExpression(
            expression = literalInt(),
            branches = listOf(
                whenBranch(type = typeReference)
            )
        )

        val references = resolve(node, globals = mapOf("T" to declaration))

        assertThat(references[typeReference], isVariableBinding(declaration))
    }

    @Test
    fun whenBranchBodiesIntroduceScopes() {
        val variableDeclaration = valStatement(name = "x", expression = literalInt())
        val variableReference = variableReference("x")

        val node = whenExpression(
            expression = literalInt(),
            branches = listOf(
                whenBranch(
                    type = staticReference("T"),
                    body = listOf(
                        variableDeclaration,
                        expressionStatement(variableReference)
                    )
                )
            )
        )

        val references = resolve(node, globals = mapOf("T" to anyDeclaration()))

        assertThat(references[variableReference], isVariableBinding(variableDeclaration))
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
            { resolve(node, globals = mapOf("Unit" to anyDeclaration())) },
            throws(has(RedeclarationError::name, equalTo("x")))
        )
    }

    @Test
    fun shapeCanBeDeclaredAfterBeingUsedInFunctionSignature() {
        val shapeReference = staticReference("X")
        val shape = shape(name = "X")

        val node = module(body = listOf(
            function(returnType = shapeReference),
            shape
        ))

        val references = resolve(node, globals = mapOf())

        assertThat(references[shapeReference], isVariableBinding(shape))
    }

    @Test
    fun shapeCanBeDeclaredAfterBeingUsedInShapeDefinition() {
        val shapeReference = staticReference("X")
        val shape = shape(name = "X")

        val node = module(body = listOf(
            shape(name = "Y", fields = listOf(
                shapeField(name = "a", type = shapeReference)
            )),
            shape
        ))

        val references = resolve(node, globals = mapOf())

        assertThat(references[shapeReference], isVariableBinding(shape))
    }

    @Test
    fun shapeTypeParametersAreAddedToScope() {
        val reference = staticReference("T")
        val typeParameter = typeParameter("T")
        val node = shape(
            typeParameters = listOf(typeParameter),
            fields = listOf(shapeField(type = reference))
        )

        val references = resolve(node, globals = mapOf())

        assertThat(references[reference], isVariableBinding(typeParameter))
    }

    @Test
    fun unionCanReferenceTypeDefinedLater() {
        val shapeReference = staticReference("X")
        val shape = shape(name = "X")

        val node = module(body = listOf(
            union("Y", listOf(shapeReference)),
            shape
        ))

        val references = resolve(node, globals = mapOf())

        assertThat(references[shapeReference], isVariableBinding(shape))
    }

    @Test
    fun unionTypeParametersAreAddedToScope() {
        val reference = staticReference("T")
        val typeParameter = typeParameter("T")
        val node = union(
            typeParameters = listOf(typeParameter),
            members = listOf(reference)
        )

        val references = resolve(node, globals = mapOf())

        assertThat(references[reference], isVariableBinding(typeParameter))
    }

    private fun resolutionContext(
        bindings: Map<String, VariableBindingNode> = mapOf(),
        isInitialised: Set<Int> = setOf(),
        globals: Map<String, VariableBindingNode> = mapOf()
    ) = ResolutionContext(
        bindings = globals + bindings,
        nodes = mutableMapOf(),
        isInitialised = HashSet(globals.values.map({ value -> value.nodeId }) + isInitialised),
        deferred = mutableMapOf()
    )

    private fun isVariableBinding(declaration: VariableBindingNode): Matcher<VariableBindingNode> {
        return equalTo(declaration)
    }
}
