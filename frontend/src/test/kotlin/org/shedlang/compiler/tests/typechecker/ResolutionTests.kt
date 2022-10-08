package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.*
import org.shedlang.compiler.types.*

class ResolutionTests {
    private val declaration = variableBinder("declaration")
    private var declarationIndex = 1
    private fun anyDeclaration(): VariableBindingNode {
        return variableBinder("declaration-" + declarationIndex++)
    }

    @Test
    fun variableReferencesAreResolved() {
        val node = variableReference("x")
        val references = resolveReferences(node, globals = mapOf(Identifier("x") to declaration))
        assertThat(references[node], isVariableBinding(declaration))
    }

    @Test
    fun whenVariableIsUninitialisedThenExceptionIsThrown() {
        val node = variableReference("x")
        val context = resolutionContext(mapOf(Identifier("x") to anyDeclaration()))

        assertThat(
            { resolveEval(node, context) },
            throws(has(UninitialisedVariableError::name, isIdentifier("x")))
        )
    }

    @Test
    fun exceptionWhenVariableNotInScope() {
        val node = variableReference("x")
        val context = resolutionContext()

        assertThat(
            { resolveEval(node, context) },
            throws(has(UnresolvedReferenceError::name, isIdentifier("x")))
        )
    }

    @Test
    fun typeReferencesAreResolved() {
        val node = typeLevelReference("X")
        val references = resolveReferences(node, globals = mapOf(Identifier("X") to declaration))
        assertThat(references[node], isVariableBinding(declaration))
    }

    @Test
    fun exceptionWhenTypeVariableNotInScope() {
        val node = typeLevelReference("X")
        val context = resolutionContext()

        assertThat(
            { resolveEval(node, context) },
            throws(has(UnresolvedReferenceError::name, isIdentifier("X")))
        )
    }

    @Test
    fun childrenAreResolved() {
        val node = variableReference("x")
        val references = resolveReferences(expressionStatement(node), globals = mapOf(Identifier("x") to declaration))
        assertThat(references[node], isVariableBinding(declaration))
    }

    @Test
    fun functionDeclarationArgumentsAreAddedToScope() {
        val reference = variableReference("x")
        val parameter = parameter(name = "x", type = typeLevelReference("Int"))
        val node = function(
            parameters = listOf(parameter),
            returnType = typeLevelReference("Int"),
            body = listOf(expressionStatement(reference))
        )

        val references = resolveReferences(node, globals = mapOf(Identifier("Int") to anyDeclaration()))

        assertThat(references[reference], isVariableBinding(parameter))
    }

    @Test
    fun functionExpressionArgumentsAreAddedToScope() {
        val reference = variableReference("x")
        val parameter = parameter(name = "x", type = typeLevelReference("Int"))
        val node = functionExpression(
            parameters = listOf(parameter),
            returnType = typeLevelReference("Int"),
            body = listOf(expressionStatement(reference))
        )

        val references = resolveReferences(node, globals = mapOf(Identifier("Int") to anyDeclaration()))

        assertThat(references[reference], isVariableBinding(parameter))
    }

    @Test
    fun functionArgumentsCanShadowExistingVariables() {
        val reference = variableReference("x")
        val parameter = parameter(name = "x", type = typeLevelReference("Int"))
        val node = function(
            parameters = listOf(parameter),
            returnType = typeLevelReference("Int"),
            body = listOf(expressionStatement(reference))
        )

        val references = resolveReferences(node, globals = mapOf(
            reference.name to anyDeclaration(),
            Identifier("Int") to anyDeclaration()
        ))

        assertThat(references[reference], isVariableBinding(parameter))
    }

    @Test
    fun typeLevelParametersInFunctionDefinitionAreAddedToScope() {
        val reference = typeLevelReference("T")
        val typeParameter = typeParameter("T")
        val node = function(
            typeLevelParameters = listOf(typeParameter),
            parameters = listOf(parameter(type = reference)),
            returnType = typeLevelReference("T"),
            body = listOf()
        )

        val references = resolveReferences(node, globals = mapOf())

        assertThat(references[reference], isVariableBinding(typeParameter))
    }

    @Test
    fun functionEffectsAreResolved() {
        val effect = typeLevelReference("Io")
        val node = function(
            effect = effect,
            returnType = typeLevelReference("Int"),
            body = listOf()
        )

        val references = resolveReferences(node, globals = mapOf(
            Identifier("Int") to anyDeclaration(),
            Identifier("Io") to declaration
        ))

        assertThat(references[effect], isVariableBinding(declaration))
    }

    @Test
    fun typeLevelParametersInFunctionTypeAreAddedToScope() {
        val positionalReference = typeLevelReference("T")
        val positionalTypeParameter = typeParameter("T")
        val namedReference = typeLevelReference("U")
        val namedTypeParameter = typeParameter("U")
        val node = functionTypeNode(
            typeLevelParameters = listOf(positionalTypeParameter, namedTypeParameter),
            positionalParameters = listOf(positionalReference),
            namedParameters = listOf(functionTypeNamedParameter(type = namedReference)),
            returnType = typeLevelReference("T")
        )

        val references = resolveReferences(node, globals = mapOf())

        assertThat(references[positionalReference], isVariableBinding(positionalTypeParameter))
        assertThat(references[namedReference], isVariableBinding(namedTypeParameter))
    }

    @Test
    fun nestedFunctionsAreResolved() {
        val innerUnitReference = typeLevelReference("Unit")
        val innerFunctionNode = function(
            name = "inner",
            returnType = innerUnitReference
        )
        val outerFunctionNode = function(
            returnType = typeLevelReference("Unit"),
            body = listOf(
                innerFunctionNode
            )
        )

        val unitDeclaration = builtinVariable("Unit", UnitMetaType)
        val references = resolveReferences(module(
            body = listOf(outerFunctionNode)
        ), globals = mapOf(Identifier("Unit") to unitDeclaration))

        assertThat(references[innerUnitReference], isVariableBinding(unitDeclaration))
    }

    @Test
    fun valIntroducesVariableToFunctionScope() {
        val reference = variableReference("x")
        val target = targetVariable(name = "x")
        val valStatement = valStatement(target = target, expression = literalInt())
        val node = function(
            parameters = listOf(),
            returnType = typeLevelReference("Int"),
            body = listOf(
                valStatement,
                expressionStatement(reference)
            )
        )

        val references = resolveReferences(node, globals = mapOf(Identifier("Int") to anyDeclaration()))
        assertThat(references[reference], isVariableBinding(target))
    }

    @Test
    fun valExpressionIsResolved() {
        val reference = variableReference("x")
        val valStatement = valStatement(name = "y", expression = reference)

        val references = resolveReferences(valStatement, globals = mapOf(Identifier("x") to declaration))
        assertThat(references[reference], isVariableBinding(declaration))
    }

    @Test
    fun importInModuleBindsTargets() {
        val target = TargetNode.Variable(
            name = Identifier("a"),
            source = anySource()
        )
        val import = import(
            target = target,
            path = ImportPath.absolute(listOf("x", "y", "z"))
        )
        val reference = variableReference("a")
        val module = module(
            imports = listOf(import),
            body = listOf(
                function(
                    body = listOf(expressionStatement(reference)),
                    returnType = typeLevelReference("Unit")
                )
            )
        )

        val references = resolveReferences(module, globals = mapOf(Identifier("Unit") to anyDeclaration()))

        assertThat(references[reference], isVariableBinding(target))
    }

    @Test
    fun importInTypesModuleIntroducesVariable() {
        val target = targetVariable("A")
        val import = import(
            target = target,
            path = ImportPath.absolute(listOf("T"))
        )
        val reference = typeLevelReference("A")
        val module = typesModule(
            imports = listOf(import),
            body = listOf(
                valType(name = "value", type = reference)
            )
        )

        val references = resolveReferences(module, globals = mapOf())

        assertThat(references[reference], isVariableBinding(target))
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

        val references = resolveReferences(node, globals = mapOf(Identifier("Unit") to anyDeclaration()))

        assertThat(references[referenceToFirst], isVariableBinding(definitionOfFirst))
        assertThat(references[referenceToSecond], isVariableBinding(definitionOfSecond))
    }

    @Test
    fun valExpressionCannotCallFunctionThatDirectlyUsesVariable() {
        val valDeclaration = valStatement(name = "x", expression = call(variableReference("f")))
        val function = function(
            name = "f",
            returnType = typeLevelReference("Int"),
            body = listOf(expressionStatement(variableReference("x")))
        )

        val node = module(body = listOf(
            function,
            valDeclaration
        ))

        assertThat(
            { resolveReferences(node, globals = mapOf(Identifier("Int") to anyDeclaration()))},
            throws(has(UninitialisedVariableError::name, isIdentifier("x")))
        )
    }

    @Test
    fun conditionOfIfStatementIsResolved() {
        val reference = variableReference("x")
        val node = ifStatement(condition = reference)

        val references = resolveReferences(node, globals = mapOf(Identifier("x") to declaration))

        assertThat(references[reference], isVariableBinding(declaration))
    }

    @Test
    fun ifStatementIntroducesScopes() {
        val trueValTarget = targetVariable(name = "x")
        val trueVal = valStatement(target = trueValTarget, expression = literalInt())
        val trueReference = variableReference("x")
        val falseValTarget = targetVariable(name = "x")
        val falseVal = valStatement(target = falseValTarget, expression = literalInt())
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

        val references = resolveReferences(node, globals = mapOf())

        assertThat(references[trueReference], isVariableBinding(trueValTarget))
        assertThat(references[falseReference], isVariableBinding(falseValTarget))
    }

    @Test
    fun expressionOfWhenExpressionIsResolved() {
        val reference = variableReference("x")
        val node = whenExpression(expression = reference)

        val references = resolveReferences(node, globals = mapOf(Identifier("x") to declaration))

        assertThat(references[reference], isVariableBinding(declaration))
    }

    @Test
    fun typesInWhenBranchesAreResolved() {
        val typeReference = typeLevelReference("T")
        val node = whenExpression(
            expression = literalInt(),
            conditionalBranches = listOf(
                whenBranch(type = typeReference)
            )
        )

        val references = resolveReferences(node, globals = mapOf(Identifier("T") to declaration))

        assertThat(references[typeReference], isVariableBinding(declaration))
    }

    @Test
    fun whenBranchBodiesIntroduceScopes() {
        val target = targetVariable(name = "x")
        val variableDeclaration = valStatement(target = target, expression = literalInt())
        val variableReference = variableReference("x")

        val node = whenExpression(
            expression = literalInt(),
            conditionalBranches = listOf(
                whenBranch(
                    type = typeLevelReference("T"),
                    body = listOf(
                        variableDeclaration,
                        expressionStatement(variableReference)
                    )
                )
            )
        )

        val references = resolveReferences(node, globals = mapOf(Identifier("T") to anyDeclaration()))

        assertThat(references[variableReference], isVariableBinding(target))
    }

    @Test
    fun whenElseBranchBodyIntroducesScope() {
        val target = targetVariable(name = "x")
        val variableDeclaration = valStatement(target = target, expression = literalInt())
        val variableReference = variableReference("x")

        val node = whenExpression(
            expression = literalInt(),
            elseBranch = listOf(
                variableDeclaration,
                expressionStatement(variableReference)
            )
        )

        val references = resolveReferences(node, globals = mapOf())

        assertThat(references[variableReference], isVariableBinding(target))
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
            { resolveReferences(node, globals = mapOf(Identifier("Unit") to anyDeclaration())) },
            throws(has(RedeclarationError::name, isIdentifier("x")))
        )
    }

    @Test
    fun shapeCanBeDeclaredAfterBeingUsedInFunctionSignature() {
        val shapeReference = typeLevelReference("X")
        val shape = shape(name = "X")

        val node = module(body = listOf(
            function(returnType = shapeReference),
            shape
        ))

        val references = resolveReferences(node, globals = mapOf())

        assertThat(references[shapeReference], isVariableBinding(shape))
    }

    @Test
    fun shapeCanBeDeclaredAfterBeingUsedInShapeDefinition() {
        val shapeReference = typeLevelReference("X")
        val shape = shape(name = "X")

        val node = module(body = listOf(
            shape(name = "Y", fields = listOf(
                shapeField(name = "a", type = shapeReference)
            )),
            shape
        ))

        val references = resolveReferences(node, globals = mapOf())

        assertThat(references[shapeReference], isVariableBinding(shape))
    }

    @Test
    fun shapeTypeParametersAreAddedToScope() {
        val reference = typeLevelReference("T")
        val typeParameter = typeParameter("T")
        val node = shape(
            typeLevelParameters = listOf(typeParameter),
            fields = listOf(shapeField(type = reference))
        )

        val references = resolveReferences(node, globals = mapOf())

        assertThat(references[reference], isVariableBinding(typeParameter))
    }

    @Test
    fun unionMemberTypeParametersAreAddedToScope() {
        val reference = typeLevelReference("T")
        val unionTypeParameter = typeParameter("T")
        val shapeTypeParameter = typeParameter("T")
        val node = union(
            typeLevelParameters = listOf(unionTypeParameter),
            members = listOf(
                unionMember(
                    "Member1",
                    typeLevelParameters = listOf(shapeTypeParameter),
                    fields = listOf(
                        shapeField(type = reference)
                    )
                )
            )
        )

        val references = resolveReferences(node, globals = mapOf())

        assertThat(references[reference], isVariableBinding(shapeTypeParameter))
    }

    @Test
    fun typeAliasIsAddedToScope() {
        val typeAliasReference = typeLevelReference("X")
        val typeAlias = typeAliasDeclaration(name = "X", expression = typeLevelReference("Int"))

        val node = module(body = listOf(
            function(returnType = typeAliasReference),
            typeAlias
        ))

        val references = resolveReferences(node, globals = mapOf(
            Identifier("Int") to anyDeclaration()
        ))

        assertThat(references[typeAliasReference], isVariableBinding(typeAlias))
    }

    @Test
    fun typeAliasExpressionIsResolved() {
        val typeAliasExpression = typeLevelReference("Int")
        val intType = BuiltinVariable(Identifier("Int"), IntMetaType)
        val typeAliasReference = typeLevelReference("X")
        val typeAlias = typeAliasDeclaration(name = "X", expression = typeAliasExpression)

        val node = module(body = listOf(
            function(returnType = typeAliasReference),
            typeAlias
        ))

        val references = resolveReferences(node, globals = mapOf(
            Identifier("Int") to intType
        ))

        assertThat(references[typeAliasExpression], isVariableBinding(intType))
    }

    @Test
    fun exportsAreResolved() {
        val target = targetVariable("x")
        val declaration = valStatement(target, literalUnit())
        val export = export("x")
        val module = module(
            exports = listOf(export),
            body = listOf(declaration)
        )

        val references = resolveReferences(module, globals = mapOf())

        assertThat(references[export], isVariableBinding(target))
    }

    private fun resolutionContext(
        bindings: Map<Identifier, VariableBindingNode> = mapOf()
    ) = ResolutionContext(
        bindings = bindings,
        nodes = mutableMapOf(),
        isInitialised = mutableSetOf(),
        definitionResolvers = mutableMapOf()
    )

    private fun isVariableBinding(declaration: VariableBindingNode): Matcher<VariableBindingNode> {
        return equalTo(declaration)
    }

    private fun resolveEval(node: Node, context: ResolutionContext) {
        val result = resolveEval(node)
        result.phaseDefine(context)
        result.phaseResolveImmediates(context)
    }
}
