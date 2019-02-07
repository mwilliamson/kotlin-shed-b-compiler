package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ImportPath
import org.shedlang.compiler.ast.VariableBindingNode
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.*

class ResolutionTests {
    private val declaration = valStatement("declaration")
    private var declarationIndex = 1
    private fun anyDeclaration(): VariableBindingNode {
        return valStatement("declaration-" + declarationIndex++)
    }

    @Test
    fun variableReferencesAreResolved() {
        val node = variableReference("x")
        val references = resolve(node, globals = mapOf(Identifier("x") to declaration))
        assertThat(references[node], isVariableBinding(declaration))
    }

    @Test
    fun whenVariableIsUninitialisedThenExceptionIsThrown() {
        val node = variableReference("x")
        val context = resolutionContext(mapOf(Identifier("x") to anyDeclaration()))

        assertThat(
            { resolve(node, context) },
            throws(has(UninitialisedVariableError::name, isIdentifier("x")))
        )
    }

    @Test
    fun exceptionWhenVariableNotInScope() {
        val node = variableReference("x")
        val context = resolutionContext()

        assertThat(
            { resolve(node, context) },
            throws(has(UnresolvedReferenceError::name, isIdentifier("x")))
        )
    }

    @Test
    fun typeReferencesAreResolved() {
        val node = staticReference("X")
        val references = resolve(node, globals = mapOf(Identifier("X") to declaration))
        assertThat(references[node], isVariableBinding(declaration))
    }

    @Test
    fun exceptionWhenTypeVariableNotInScope() {
        val node = staticReference("X")
        val context = resolutionContext()

        assertThat(
            { resolve(node, context) },
            throws(has(UnresolvedReferenceError::name, isIdentifier("X")))
        )
    }

    @Test
    fun childrenAreResolved() {
        val node = variableReference("x")
        val references = resolve(expressionStatement(node), globals = mapOf(Identifier("x") to declaration))
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

        val references = resolve(node, globals = mapOf(Identifier("Int") to anyDeclaration()))

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

        val references = resolve(node, globals = mapOf(Identifier("Int") to anyDeclaration()))

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
            Identifier("Int") to anyDeclaration()
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
        val effect = staticReference("Io")
        val node = function(
            effects = listOf(effect),
            returnType = staticReference("Int"),
            body = listOf()
        )

        val references = resolve(node, globals = mapOf(
            Identifier("Int") to anyDeclaration(),
            Identifier("Io") to declaration
        ))

        assertThat(references[effect], isVariableBinding(declaration))
    }

    @Test
    fun staticParametersInFunctionTypeAreAddedToScope() {
        val positionalReference = staticReference("T")
        val positionalTypeParameter = typeParameter("T")
        val namedReference = staticReference("U")
        val namedTypeParameter = typeParameter("U")
        val node = functionTypeNode(
            staticParameters = listOf(positionalTypeParameter, namedTypeParameter),
            positionalParameters = listOf(positionalReference),
            namedParameters = listOf(parameter(type = namedReference)),
            returnType = staticReference("T")
        )

        val references = resolve(node, globals = mapOf())

        assertThat(references[positionalReference], isVariableBinding(positionalTypeParameter))
        assertThat(references[namedReference], isVariableBinding(namedTypeParameter))
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

        val references = resolve(node, globals = mapOf(Identifier("Int") to anyDeclaration()))
        assertThat(references[reference], isVariableBinding(valStatement))
    }

    @Test
    fun valExpressionIsResolved() {
        val reference = variableReference("x")
        val valStatement = valStatement(name = "y", expression = reference)

        val references = resolve(valStatement, globals = mapOf(Identifier("x") to declaration))
        assertThat(references[reference], isVariableBinding(declaration))
    }

    @Test
    fun importInModuleIntroducesVariable() {
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

        val references = resolve(module, globals = mapOf(Identifier("Unit") to anyDeclaration()))

        assertThat(references[reference], isVariableBinding(import))
    }

    @Test
    fun importInTypesModuleIntroducesVariable() {
        val import = import(path = ImportPath.absolute(listOf("T")))
        val reference = staticReference("T")
        val module = typesModule(
            imports = listOf(import),
            body = listOf(
                valType(name = "value", type = reference)
            )
        )

        val references = resolve(module, globals = mapOf())

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

        val references = resolve(node, globals = mapOf(Identifier("Unit") to anyDeclaration()))

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
            { resolve(node, globals = mapOf(Identifier("Int") to anyDeclaration()))},
            throws(has(UninitialisedVariableError::name, isIdentifier("x")))
        )
    }

    @Test
    fun conditionOfIfStatementIsResolved() {
        val reference = variableReference("x")
        val node = ifStatement(condition = reference)

        val references = resolve(node, globals = mapOf(Identifier("x") to declaration))

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

        val references = resolve(node, globals = mapOf(Identifier("x") to declaration))

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

        val references = resolve(node, globals = mapOf(Identifier("T") to declaration))

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

        val references = resolve(node, globals = mapOf(Identifier("T") to anyDeclaration()))

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
            { resolve(node, globals = mapOf(Identifier("Unit") to anyDeclaration())) },
            throws(has(RedeclarationError::name, isIdentifier("x")))
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
            staticParameters = listOf(typeParameter),
            fields = listOf(shapeField(type = reference))
        )

        val references = resolve(node, globals = mapOf())

        assertThat(references[reference], isVariableBinding(typeParameter))
    }

    @Test
    fun unionTypeParametersAreAddedToScope() {
        // TODO:
//        val reference = staticReference("T")
//        val typeParameter = typeParameter("T")
//        val node = union(
//            staticParameters = listOf(typeParameter),
//            members = listOf(reference)
//        )
//
//        val references = resolve(node, globals = mapOf())
//
//        assertThat(references[reference], isVariableBinding(typeParameter))
    }

    private fun resolutionContext(
        bindings: Map<Identifier, VariableBindingNode> = mapOf()
    ) = ResolutionContext(
        bindings = bindings,
        nodes = mutableMapOf(),
        isInitialised = mutableSetOf(),
        deferred = mutableMapOf()
    )

    private fun isVariableBinding(declaration: VariableBindingNode): Matcher<VariableBindingNode> {
        return equalTo(declaration)
    }
}
