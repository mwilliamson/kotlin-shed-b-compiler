package org.shedlang.compiler.backends.python.tests

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.*
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.python.*
import org.shedlang.compiler.backends.python.ast.*
import org.shedlang.compiler.parser.parse
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.ResolvedReferencesMap
import org.shedlang.compiler.typechecker.resolve
import org.shedlang.compiler.types.Discriminator
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.MetaType
import org.shedlang.compiler.types.UnitType
import java.math.BigInteger

class CodeGeneratorTests {
    @Test
    fun moduleWithoutOtherModulesIsNotPackage() {
        assertThat(
            isPackage(listOf(listOf("X", "Y")), listOf("X", "Y")),
            equalTo(false)
        )
    }

    @Test
    fun moduleWithOtherModulesInOtherPackagesIsNotPackage() {
        assertThat(
            isPackage(listOf(listOf("X", "Y"), listOf("X", "Z"), listOf("A", "B", "C"), listOf("D")), listOf("X", "Y")),
            equalTo(false)
        )
    }

    @Test
    fun moduleWithOtherModulesInSubPackageIsPackage() {
        assertThat(
            isPackage(listOf(listOf("X", "Y"), listOf("X", "Y", "Z")), listOf("X", "Y")),
            equalTo(true)
        )
    }

    @Test
    fun moduleWithOtherModulesInSubSubPackageIsPackage() {
        assertThat(
            isPackage(listOf(listOf("X", "Y"), listOf("X", "Y", "Z", "A")), listOf("X", "Y")),
            equalTo(true)
        )
    }

    private fun isPackage(moduleNames: List<List<String>>, moduleName: List<String>): Boolean {
        val moduleSet = ModuleSet(modules = moduleNames.map { moduleName ->
            Module.Shed(
                name = moduleName.map(::Identifier),
                node = module(),
                references = ResolvedReferencesMap.EMPTY,
                type = moduleType(),
                types = EMPTY_TYPES
            )
        })
        return isPackage(moduleSet, moduleName.map(::Identifier))
    }

    @Test
    fun emptyModuleGeneratesEmptyModule() {
        val shed = module(body = listOf())

        val node = generateCode(shed)

        assertThat(node, isPythonModule(equalTo(listOf())))
    }

    @Test
    fun relativeModuleImportsGeneratePythonImports() {
        val shed = module(imports = listOf(import(ImportPath.relative(listOf("x", "y")))))

        val node = generateCode(shed)

        assertThat(node, isPythonModule(
            body = isSequence(
                isPythonImportFrom(
                    module = equalTo(".x"),
                    names = isSequence(equalTo("y" to "y"))
                )
            )
        ))
    }

    @Test
    fun whenModuleIsPackageThenRelativeModuleImportGoesUpOnePackage() {
        val shed = module(imports = listOf(import(ImportPath.relative(listOf("x", "y")))))

        val node = generateCode(shed, context(isPackage = true))

        assertThat(node, isPythonModule(
            body = isSequence(
                isPythonImportFrom(
                    module = equalTo("..x"),
                    names = isSequence(equalTo("y" to "y"))
                )
            )
        ))
    }

    @Test
    fun absoluteModuleImportsGeneratePythonImports() {
        val shed = module(imports = listOf(import(ImportPath.absolute(listOf("x", "y")))))

        val node = generateCode(shed)

        assertThat(node, isPythonModule(
            body = isSequence(
                isPythonImportFrom(
                    module = equalTo("shed.x"),
                    names = isSequence(equalTo("y" to "y"))
                )
            )
        ))
    }

    @Test
    fun importImportsModuleUsingPythonisedName() {
        val shed = module(imports = listOf(import(ImportPath.relative(listOf("oneTwo", "threeFour")))))

        val node = generateCode(shed)

        assertThat(node, isPythonModule(
            body = isSequence(
                isPythonImportFrom(
                    module = equalTo(".oneTwo"),
                    names = isSequence(equalTo("threeFour" to "three_four"))
                )
            )
        ))
    }

    @Test
    fun moduleGeneratesModule() {
        val shed = module(body = listOf(function(name = "f")))

        val node = generateCode(shed)

        assertThat(node, isPythonModule(
            body = isSequence(isPythonFunction(name = equalTo("f")))
        ))
    }

    @Test
    fun typeAliasGeneratesNothing() {
        val shed = typeAliasDeclaration("Size", staticReference("Int"))

        val pythonNodes = generateModuleStatementCode(shed, context())

        assertThat(pythonNodes, isSequence())
    }

    @Test
    fun shapeGeneratesClass() {
        val shed = shape(
            name = "OneTwoThree",
            fields = listOf(
                shapeField("a", staticReference("Int"), value = null),
                shapeField("b", staticReference("Int"), value = literalInt(0))
            )
        )
        val shapeType = shapeType(
            fields = listOf(
                field("a", type = IntType, isConstant = false),
                field("b", type = IntType, isConstant = true)
            )
        )

        val types = typesMap(
            variableTypes = mapOf(shed to MetaType(shapeType))
        )

        val node = generateModuleStatementCode(shed, context(types = types)).single()

        assertThat(node, isPythonClass(
            name = equalTo("OneTwoThree"),
            body = isSequence(
                isPythonAssignment("b", isPythonIntegerLiteral(0)),
                isPythonFunction(
                    name = equalTo("__init__"),
                    parameters = isSequence(equalTo("self"), equalTo("a")),
                    body = isSequence(
                        isPythonAssignment(
                            target = isPythonAttributeAccess(
                                receiver = isPythonVariableReference("self"),
                                attributeName = equalTo("a")
                            ),
                            expression = isPythonVariableReference("a")
                        )
                    )
                )
            )
        ))
    }

    @Test
    fun shapeWithOnlyConstantFieldsUsesDefaultInit() {
        val shed = shape(
            name = "OneTwoThree",
            fields = listOf(
                shapeField("b", staticReference("Int"), value = literalInt(0))
            )
        )
        val shapeType = shapeType(
            fields = listOf(
                field("b", type = IntType, isConstant = true)
            )
        )

        val types = typesMap(
            variableTypes = mapOf(shed to MetaType(shapeType))
        )

        val node = generateModuleStatementCode(shed, context(types = types)).single()

        assertThat(node, isPythonClass(
            name = equalTo("OneTwoThree"),
            body = isSequence(
                isPythonAssignment("b", isPythonIntegerLiteral(0))
            )
        ))
    }

    @Test
    fun unionGeneratesShapesForEachMember() {
        val member1Node = unionMember("Member1")
        val member2Node = unionMember("Member2")
        val shed = union("X", listOf(member1Node, member2Node))

        val member1Type = shapeType("Member1")
        val member2Type = shapeType("Member2")

        val types = typesMap(
            variableTypes = mapOf(
                member1Node to MetaType(member1Type),
                member2Node to MetaType(member2Type)
            )
        )

        val nodes = generateModuleStatementCode(shed, context(types = types))

        assertThat(nodes, isSequence(
            isPythonClass(
                name = equalTo("Member1")
            ),
            isPythonClass(
                name = equalTo("Member2")
            )
        ))
    }

    @Test
    fun functionDeclarationAsModuleStatementGeneratesFunctionWithPythonisedName() {
        assertFunctionDeclarationGeneratesFunctionWithPythonisedName { function ->
            generateCodeForModuleStatement(function).single()
        }
    }

    @Test
    fun functionDeclarationAsFunctionStatementGeneratesFunctionWithPythonisedName() {
        assertFunctionDeclarationGeneratesFunctionWithPythonisedName { function ->
            generateCodeForFunctionStatement(function).single()
        }
    }

    fun assertFunctionDeclarationGeneratesFunctionWithPythonisedName(
        generateCode: (function: FunctionDeclarationNode) -> PythonStatementNode
    ) {
        val shed = function(
            name = "oneTwoThree",
            parameters = listOf(parameter("x"), parameter("y")),
            namedParameters = listOf(parameter("z")),
            body = listOf(expressionStatement(literalInt(42)))
        )

        val node = generateCode(shed)

        assertThat(node, isPythonFunction(
            name = equalTo("one_two_three"),
            parameters = isSequence(equalTo("x"), equalTo("y"), equalTo("z")),
            body = isSequence(isPythonExpressionStatement(isPythonIntegerLiteral(42)))
        ))
    }

    @Test
    fun functionExpressionWithNoStatementsGeneratesLambda() {
        val shed = functionExpression(
            parameters = listOf(parameter("x"), parameter("y")),
            namedParameters = listOf(parameter("z")),
            body = listOf()
        )

        val node = generateCode(shed)

        assertThat(node, isGeneratedExpression(isPythonLambda(
            parameters = isSequence(equalTo("x"), equalTo("y"), equalTo("z")),
            body = isPythonNone()
        )))
    }

    @Test
    fun functionExpressionWithSingleExpressionStatementGeneratesLambda() {
        val shed = functionExpression(
            parameters = listOf(parameter("x"), parameter("y")),
            body = listOf(expressionStatement(literalInt(42)))
        )

        val node = generateCode(shed)

        assertThat(node, isGeneratedExpression(isPythonLambda(
            parameters = isSequence(equalTo("x"), equalTo("y")),
            body = isPythonIntegerLiteral(42)
        )))
    }

    @Test
    fun functionExpressionWithNonEmptyBodyThatIsntSingleReturnGeneratesAuxiliaryFunction() {
        val shed = functionExpression(
            parameters = listOf(parameter("x"), parameter("y")),
            body = listOf(valStatement("z", literalInt(42)))
        )

        val node = generateCode(shed)
        val auxiliaryFunction = node.statements.single()
        assertThat(auxiliaryFunction, isPythonFunction(
            parameters = isSequence(equalTo("x"), equalTo("y")),
            body = isSequence(isPythonAssignment(isPythonVariableReference("z"), isPythonIntegerLiteral(42)))
        ))

        assertThat(node.value, isPythonVariableReference((auxiliaryFunction as PythonFunctionNode).name))
    }

    @Test
    fun nonReturningExpressionStatementGeneratesExpressionStatement() {
        val shed = expressionStatement(literalInt(42), isReturn = false)
        val node = generateCodeForFunctionStatement(shed)
        assertThat(node, isSequence(isPythonExpressionStatement(isPythonIntegerLiteral(42))))
    }

    @Test
    fun returningExpressionStatementGeneratesReturnStatement() {
        val shed = expressionStatement(literalInt(42), isReturn = true)
        val node = generateCodeForFunctionStatement(shed)
        assertThat(node, isSequence(isPythonReturn(isPythonIntegerLiteral(42))))
    }

    @Test
    fun ifExpressionGeneratesIfStatementWithVariableAssignment() {
        val shed = ifExpression(
            conditionalBranches = listOf(
                conditionalBranch(
                    condition = literalInt(42),
                    body = listOf(
                        expressionStatement(literalInt(0), isReturn = false),
                        expressionStatement(literalInt(1), isReturn = true)
                    )
                )
            ),
            elseBranch = listOf(expressionStatement(literalInt(2), isReturn = true))
        )

        val generatedExpression = generateCode(shed)
        val reference = generatedExpression.value as PythonVariableReferenceNode

        val function = generatedExpression.statements.single()
        assertThat(function, isPythonIfStatement(
            conditionalBranches = isSequence(
                isPythonConditionalBranch(
                    condition = isPythonIntegerLiteral(42),
                    body = isSequence(
                        isPythonExpressionStatement(isPythonIntegerLiteral(0)),
                        isPythonAssignment(
                            isPythonVariableReference(reference.name),
                            isPythonIntegerLiteral(1)
                        )
                    )
                )
            ),
            elseBranch = isSequence(
                isPythonAssignment(
                    isPythonVariableReference(reference.name),
                    isPythonIntegerLiteral(2)
                )
            )
        ))
    }

    @Test
    fun returningIfStatementGeneratesIfStatementWithReturns() {
        val shed = function(
            body = listOf(
                expressionStatement(
                    ifExpression(
                        conditionalBranches = listOf(
                            conditionalBranch(
                                condition = literalInt(42),
                                body = listOf(
                                    expressionStatement(literalInt(0), isReturn = false),
                                    expressionStatement(literalInt(1), isReturn = true)
                                )
                            )
                        ),
                        elseBranch = listOf(expressionStatement(literalInt(2), isReturn = true))
                    ),
                    isReturn = true
                )
            )
        )

        val generatedCode = generateModuleStatementCode(
            shed,
            context()
        )

        assertThat(generatedCode.single(), isPythonFunction(
            body = isSequence(
                isPythonIfStatement(
                    conditionalBranches = isSequence(
                        isPythonConditionalBranch(
                            condition = isPythonIntegerLiteral(42),
                            body = isSequence(
                                isPythonExpressionStatement(isPythonIntegerLiteral(0)),
                                isPythonReturn(isPythonIntegerLiteral(1))
                            )
                        )
                    ),
                    elseBranch = isSequence(
                        isPythonReturn(isPythonIntegerLiteral(2))
                    )
                )
            )
        ))
    }

    @Test
    fun whenExpressionGeneratesIfStatementsWithAssignmentToVariable() {
        val variableDeclaration = valStatement("x")
        val variableReference = variableReference("x")
        val typeReference = staticReference("T")
        val conditionExpression = fieldAccess(variableReference, "f")
        val shed = whenExpression(
            conditionExpression,
            branches = listOf(
                whenBranch(
                    typeReference,
                    listOf(
                        expressionStatement(literalInt(42), isReturn = true)
                    )
                )
            ),
            elseBranch = listOf(
                expressionStatement(literalInt(47), isReturn = true)
            )
        )

        val generatedExpression = generateExpressionCode(shed, context(
            references = mapOf(
                variableReference to variableDeclaration
            ),
            types = typesMap(
                discriminators = mapOf(
                    Pair(conditionExpression, typeReference) to discriminator(symbolType(listOf("M"), "@A"), "tag")
                )
            )
        ))
        val reference = generatedExpression.value as PythonVariableReferenceNode

        assertThat(generatedExpression.statements, isSequence(
            isPythonAssignment(
                target = isPythonVariableReference("f"),
                expression = isPythonAttributeAccess(
                    receiver = isPythonVariableReference("x"),
                    attributeName = equalTo("f")
                )
            ),
            isPythonIfStatement(
                conditionalBranches = isSequence(
                    isPythonConditionalBranch(
                        condition = isPythonTypeCondition(
                            expression = isPythonVariableReference("f"),
                            discriminator = discriminator(symbolType(listOf("M"), "@A"), "tag")
                        ),
                        body = isSequence(
                            isPythonAssignment(
                                target = isPythonVariableReference(reference.name),
                                expression = isPythonIntegerLiteral(42)
                            )
                        )
                    )
                ),
                elseBranch = isSequence(
                    isPythonAssignment(
                        target = isPythonVariableReference(reference.name),
                        expression = isPythonIntegerLiteral(47)
                    )
                )
            )
        ))
    }

    @Test
    fun variableReferenceInWhenIsUsedWithoutAssignmentToTemporaryVariable() {
        val variableDeclaration = valStatement("x")
        val variableReference = variableReference("x")
        val typeReference = staticReference("T")
        val shed = whenExpression(
            variableReference,
            branches = listOf(
                whenBranch(
                    typeReference,
                    listOf(
                        expressionStatement(literalInt(42), isReturn = true)
                    )
                )
            )
        )

        val generatedExpression = generateExpressionCode(shed, context(
            references = mapOf(
                variableReference to variableDeclaration
            ),
            types = typesMap(
                discriminators = mapOf(
                    Pair(variableReference, typeReference) to discriminator(symbolType(listOf("M"), "@A"), "tag")
                )
            )
        ))
        val reference = generatedExpression.value as PythonVariableReferenceNode

        assertThat(generatedExpression.statements, isSequence(
            isPythonIfStatement(
                conditionalBranches = isSequence(
                    isPythonConditionalBranch(
                        condition = isPythonTypeCondition(
                            expression = isPythonVariableReference("x"),
                            discriminator = discriminator(symbolType(listOf("M"), "@A"), "tag")
                        ),
                        body = isSequence(
                            isPythonAssignment(
                                target = isPythonVariableReference(reference.name),
                                expression = isPythonIntegerLiteral(42)
                            )
                        )
                    )
                ),
                elseBranch = isSequence()
            )
        ))
    }

    @Test
    fun returningWhenStatementGeneratesIfStatementsWithReturns() {
        val variableDeclaration = valStatement("x")
        val variableReference = variableReference("x")
        val typeDeclaration = typeParameter("T")
        val typeReference = staticReference("T")
        val shed = function(
            body = listOf(
                expressionStatement(
                    whenExpression(
                        variableReference,
                        branches = listOf(
                            whenBranch(
                                typeReference,
                                listOf(
                                    expressionStatement(literalInt(42), isReturn = true)
                                )
                            )
                        ),
                        elseBranch = listOf(
                            expressionStatement(literalInt(47), isReturn = true)
                        )
                    ),
                    isReturn = true
                )
            )
        )

        val generatedCode = generateModuleStatementCode(
            shed,
            context(
                references = mapOf(
                    variableReference to variableDeclaration,
                    typeReference to typeDeclaration
                ),
                types = typesMap(
                    discriminators = mapOf(
                        Pair(variableReference, typeReference) to discriminator(symbolType(listOf("M"), "@A"), "tag")
                    )
                )
            )
        )

        assertThat(generatedCode.single(), isPythonFunction(
            body = isSequence(
                isPythonIfStatement(
                    conditionalBranches = isSequence(
                        isPythonConditionalBranch(
                            condition = isPythonTypeCondition(
                                expression = isPythonVariableReference("x"),
                                discriminator = discriminator(symbolType(listOf("M"), "@A"), "tag")
                            ),
                            body = isSequence(
                                isPythonReturn(isPythonIntegerLiteral(42))
                            )
                        )
                    ),
                    elseBranch = isSequence(
                        isPythonReturn(isPythonIntegerLiteral(47))
                    )
                )
            )
        ))
    }

    @Test
    @Disabled("TODO: work out what to do with this test")
    fun whenSeparateScopesHaveSameNameInSamePythonScopeThenVariablesAreRenamed() {
        val trueVal = valStatement(name = "x")
        val falseVal = valStatement(name = "x")

        val trueReference = variableReference("x")
        val falseReference = variableReference("x")

        val references: Map<ReferenceNode, VariableBindingNode> = mapOf(
            trueReference to trueVal,
            falseReference to falseVal
        )

        val shed = ifExpression(
            literalBool(),
            listOf(
                trueVal,
                expressionStatement(trueReference)
            ),
            listOf(
                falseVal,
                expressionStatement(falseReference)
            )
        )

        val generatedCode = generateExpressionCode(shed, context(references = references))

        assertThat((generatedCode.statements.single() as PythonFunctionNode).body, isSequence(
            isPythonIfStatement(
                conditionalBranches = isSequence(
                    isPythonConditionalBranch(
                        body = isSequence(
                            isPythonAssignment(target = isPythonVariableReference("x")),
                            isPythonExpressionStatement(isPythonVariableReference("x"))
                        )
                    )
                ),
                elseBranch = isSequence(
                    isPythonAssignment(target = isPythonVariableReference("x_1")),
                    isPythonExpressionStatement(isPythonVariableReference("x_1"))
                )
            )
        ))
    }

    @Test
    fun valGeneratesAssignmentWithPythonisedName() {
        val shed = valStatement(name = "oneTwoThree", expression = literalInt(42))

        val node = generateCodeForFunctionStatement(shed)

        assertThat(node, isSequence(
            isPythonAssignment(
                target = isPythonVariableReference("one_two_three"),
                expression = isPythonIntegerLiteral(42)
            )
        ))
    }

    @Test
    fun whenValStatementHasSpillingExpressionThenTemporaryIsNotUsed() {
        val shed = valStatement(
            name = "x",
            expression = ifExpression(
                literalBool(true),
                listOf(expressionStatement(literalInt(0), isReturn = true)),
                listOf(expressionStatement(literalInt(1), isReturn = true))
            )
        )

        val node = generateCodeForFunctionStatement(shed)

        assertThat("was: " + serialise(node), node, isSequence(
            isPythonIfStatement(
                conditionalBranches = isSequence(
                    isPythonConditionalBranch(
                        condition = isPythonBooleanLiteral(true),
                        body = isSequence(
                            isPythonAssignment(
                                target = isPythonVariableReference("x"),
                                expression = isPythonIntegerLiteral(0)
                            )
                        )
                    )
                ),
                elseBranch = isSequence(
                    isPythonAssignment(
                        target = isPythonVariableReference("x"),
                        expression = isPythonIntegerLiteral(1)
                    )
                )
            )
        ))
    }

    @Test
    fun unitLiteralGeneratesNone() {
        val shed = literalUnit()
        val node = generateCode(shed)
        assertThat(node, isGeneratedExpression(isPythonNone()))
    }

    @Test
    fun booleanLiteralGeneratesBooleanLiteral() {
        val shed = literalBool(true)

        val node = generateCode(shed)

        assertThat(node, isGeneratedExpression(isPythonBooleanLiteral(true)))
    }

    @Test
    fun integerLiteralGeneratesIntegerLiteral() {
        val shed = literalInt(42)

        val node = generateCode(shed)

        assertThat(node, isGeneratedExpression(isPythonIntegerLiteral(42)))
    }

    @Test
    fun stringLiteralGeneratesStringLiteral() {
        val shed = literalString("<string>")
        val node = generateCode(shed)
        assertThat(node, isGeneratedExpression(isPythonStringLiteral("<string>")))
    }

    @Test
    fun codePointLiteralGeneratesStringLiteral() {
        val shed = literalCodePoint('!')
        val node = generateCode(shed)
        assertThat(node, isGeneratedExpression(isPythonStringLiteral("!")))
    }

    @Test
    fun symbolNameGeneratesString() {
        val shed = symbolName("@blah")
        val node = generateExpressionCode(shed, context(moduleName = listOf("A", "B")))
        assertThat(node, isGeneratedExpression(isPythonStringLiteral("A.B.@blah")))
    }

    @Test
    fun tupleGeneratesTuple() {
        val shed = tupleNode(listOf(literalInt(42), literalBool(true)))
        val node = generateExpressionCode(shed, context())
        assertThat(node, isGeneratedExpression(isPythonTuple(isSequence(
            isPythonIntegerLiteral(42),
            isPythonBooleanLiteral(true)
        ))))
    }

    @Test
    fun variableReferenceGeneratesVariableReference() {
        val declaration = parameter("x")
        val shed = variableReference("x")

        val node = generateExpressionCode(shed, context(references = mapOf(shed to declaration)))

        assertThat(node, isGeneratedExpression(isPythonVariableReference("x")))
    }

    @Test
    fun variableReferenceToBuiltinGeneratesEquivalentPythonBuiltin() {
        val shed = variableReference("intToString")

        val node = generateExpressionCode(shed, context(references = mapOf(shed to Builtins.intToString)))

        assertThat(node, isGeneratedExpression(isPythonVariableReference("str")))
    }

    @Test
    fun notOperationGeneratesNotOperation() {
        val shed = unaryOperation(
            operator = UnaryOperator.NOT,
            operand = literalBool(true)
        )

        val node = generateCode(shed)

        assertThat(node, isGeneratedExpression(isPythonUnaryOperation(
            operator = equalTo(PythonUnaryOperator.NOT),
            operand = isPythonBooleanLiteral(true)
        )))
    }

    @Test
    fun unaryMinusOperationGeneratesUnaryMinusOperation() {
        val shed = unaryOperation(
            operator = UnaryOperator.MINUS,
            operand = literalBool(true)
        )

        val node = generateCode(shed)

        assertThat(node, isGeneratedExpression(isPythonUnaryOperation(
            operator = equalTo(PythonUnaryOperator.MINUS),
            operand = isPythonBooleanLiteral(true)
        )))
    }

    @TestFactory
    fun binaryOperationGeneratesBinaryOperation(): List<DynamicTest> {
        return listOf(
            BinaryOperator.ADD to PythonBinaryOperator.ADD,
            BinaryOperator.SUBTRACT to PythonBinaryOperator.SUBTRACT,
            BinaryOperator.MULTIPLY to PythonBinaryOperator.MULTIPLY,
            BinaryOperator.EQUALS to PythonBinaryOperator.EQUALS,
            BinaryOperator.LESS_THAN to PythonBinaryOperator.LESS_THAN,
            BinaryOperator.LESS_THAN_OR_EQUAL to PythonBinaryOperator.LESS_THAN_OR_EQUAL,
            BinaryOperator.GREATER_THAN to PythonBinaryOperator.GREATER_THAN,
            BinaryOperator.GREATER_THAN_OR_EQUAL to PythonBinaryOperator.GREATER_THAN_OR_EQUAL,
            BinaryOperator.AND to PythonBinaryOperator.AND,
            BinaryOperator.OR to PythonBinaryOperator.OR
        ).map({ operator ->  DynamicTest.dynamicTest(
            operator.first.toString(), {
                val shed = binaryOperation(
                    operator = operator.first,
                    left = literalInt(0),
                    right = literalInt(1)
                )

                val node = generateCode(shed)

                assertThat(node, isGeneratedExpression(isPythonBinaryOperation(
                    operator = equalTo(operator.second),
                    left = isPythonIntegerLiteral(0),
                    right = isPythonIntegerLiteral(1)
                )))
            })
        })
    }

    private data class SpillingOrderTestCase(
        val name: String,
        val generatedCode: GeneratedCode<PythonExpressionNode>
    )

    @TestFactory
    fun spillingPreservesEvaluationOrder(): List<DynamicTest> {
        val earlierFunctionDeclaration = declaration("earlier")
        val laterFunctionDeclaration = declaration("later")
        val earlierFunctionReference = variableReference("earlier")
        val laterFunctionReference = variableReference("later")

        val receiverDeclaration = declaration("receiver")
        val receiverReference = variableReference("receiver")

        val earlierExpression = call(earlierFunctionReference)
        val laterExpression = ifExpression(
            literalBool(true),
            listOf(expressionStatement(call(laterFunctionReference), isReturn = true)),
            listOf(expressionStatement(call(laterFunctionReference), isReturn = true))
        )

        val references: Map<ReferenceNode, VariableBindingNode> = mapOf(
            earlierFunctionReference to earlierFunctionDeclaration,
            laterFunctionReference to laterFunctionDeclaration,
            receiverReference to receiverDeclaration
        )
        val testCases = listOf(
            SpillingOrderTestCase(
                "binary operation",
                generatedCode = run {
                    val shed = binaryOperation(
                        operator = BinaryOperator.ADD,
                        left = earlierExpression,
                        right = laterExpression
                    )
                    generateExpressionCode(shed, context = context(references = references))
                }
            ),
            SpillingOrderTestCase(
                "call: positional arguments",
                generatedCode = run {
                    val shed = call(
                        receiver = receiverReference,
                        positionalArguments = listOf(earlierExpression, laterExpression)
                    )
                    generateExpressionCode(shed, context = context(references = references))
                }
            ),
            SpillingOrderTestCase(
                "call: named arguments",
                generatedCode = run {
                    val shed = call(
                        receiver = receiverReference,
                        namedArguments = listOf(
                            callNamedArgument("x", earlierExpression),
                            callNamedArgument("y", laterExpression)
                        )
                    )
                    generateExpressionCode(shed, context = context(references = references))
                }
            ),
            SpillingOrderTestCase(
                "call: receiver before positional arguments",
                generatedCode = run {
                    val shed = call(
                        receiver = earlierExpression,
                        positionalArguments = listOf(laterExpression)
                    )
                    generateExpressionCode(shed, context = context(references = references))
                }
            ),
            SpillingOrderTestCase(
                "call: positional arguments before named arguments",
                generatedCode = run {
                    val shed = call(
                        receiver = receiverReference,
                        positionalArguments = listOf(earlierExpression),
                        namedArguments = listOf(callNamedArgument("x", laterExpression))
                    )
                    generateExpressionCode(shed, context = context(references = references))
                }
            )
        )

        return testCases.map { testCase ->
            DynamicTest.dynamicTest(testCase.name, {
                val evaluationOrder = pythonEvaluationOrder(testCase.generatedCode)
                val earlierIndex = evaluationOrder.firstIndex(
                    cast(isPythonFunctionCall(isPythonVariableReference("earlier")))
                )
                val laterIndex = evaluationOrder.firstIndex(
                    cast(isPythonFunctionCall(isPythonVariableReference("later")))
                )

                assertThat(earlierIndex, lessThan(laterIndex))
            })
        }
    }

    @Test
    fun isOperationGeneratesTypeConditionCheck() {
        val variableDeclaration = declaration("x")
        val variableReference = variableReference("x")
        val shapeReference = staticReference("Shape1")
        val shed = isOperation(variableReference, shapeReference)

        val context = context(
            references = mapOf(
                variableReference to variableDeclaration
            ),
            types = typesMap(
                discriminators = mapOf(
                    Pair(variableReference, shapeReference) to discriminator(symbolType(listOf("M"), "@A"), "tag")
                )
            )
        )
        val node = generateExpressionCode(shed, context)

        assertThat(node, isGeneratedExpression(isPythonTypeCondition(
            isPythonVariableReference("x"),
            discriminator(symbolType(listOf("M"), "@A"), "tag")
        )))
    }

    @Test
    fun functionCallGeneratesFunctionCall() {
        val declaration = parameter("f")
        val function = variableReference("f")
        val shed = call(
            function,
            positionalArguments = listOf(literalInt(42)),
            namedArguments = listOf(callNamedArgument("x", literalBool(true)))
        )

        val node = generateExpressionCode(shed, context(references = mapOf(function to declaration)))

        assertThat(node, isGeneratedExpression(isPythonFunctionCall(
            isPythonVariableReference("f"),
            isSequence(isPythonIntegerLiteral(42)),
            isSequence(isPair(equalTo("x"), isPythonBooleanLiteral(true)))
        )))
    }

    @Test
    fun directlyRecursiveFunctionsAreConvertedToWhileLoops() {
        val shedSource = """
            fun factorial(n: Int, acc: Int) -> Int {
                if (n == 1) {
                    acc
                } else {
                    factorial(n - 1, acc * n)
                }
            }
        """.trimIndent()
        val expectedPython = """
            def factorial(n, acc):
                while True:
                    if n == 1:
                        return acc
                    else:
                        n_1 = n - 1
                        acc_1 = acc * n
                        n = n_1
                        acc = acc_1
        """.trimIndent()
        val shed = parse("<string>", shedSource)
        val intBuiltin = builtinType("Int", IntType)
        val references = resolve(shed, globals = mapOf(Identifier("Int") to intBuiltin))
        val node = generateCode(shed, references = references)

        assertThat(serialise(node).trim(), equalTo(expectedPython))
    }

    @Test
    fun directlyRecursiveCallsDoNotReassignArgumentsThatDoNotChange() {
        val shedSource = """
            fun factorial(i: Int, n: Int, acc: Int) -> Int {
                if (i == n) {
                    acc
                } else {
                    factorial(i + 1, n, acc * i)
                }
            }
        """.trimIndent()
        val expectedPython = """
            def factorial(i, n, acc):
                while True:
                    if i == n:
                        return acc
                    else:
                        i_1 = i + 1
                        acc_1 = acc * i
                        i = i_1
                        acc = acc_1
        """.trimIndent()
        val shed = parse("<string>", shedSource)
        val intBuiltin = builtinType("Int", IntType)
        val references = resolve(shed, globals = mapOf(Identifier("Int") to intBuiltin))
        val node = generateCode(shed, references = references)

        assertThat(serialise(node).trim(), equalTo(expectedPython))
    }

    @Test
    fun directlyRecursiveFunctionsContainingFunctionExpressionsAreNotConvertedToWhileLoops() {
        val shedSource = """
            fun f(n: Int, g: Fun () -> Unit) -> Unit {
                if (n == 1) {
                    g()
                } else {
                    f(n - 1, fun() {
                        g();
                        g();
                    })
                }
            }
        """.trimIndent()
        val expectedPython = """
            def f(n, g):
                if n == 1:
                    return g()
                else:
                    def anonymous():
                        g()
                        g()

                    return f(n - 1, anonymous)
        """.trimIndent()
        val shed = parse("<string>", shedSource)
        val intBuiltin = builtinType("Int", IntType)
        val unitBuiltin = builtinType("Unit", UnitType)
        val references = resolve(shed, globals = mapOf(
            Identifier("Int") to intBuiltin,
            Identifier("Unit") to unitBuiltin
        ))
        val node = generateCode(shed, references = references)

        assertThat(serialise(node).trim(), equalTo(expectedPython))
    }

    @Test
    fun directlyRecursiveFunctionsWithSpilledArgumentsDoNotDuplicateArguments() {
        val shedSource = """
            fun f(n: Int) -> Int {
                f(
                    if (n == 1) {
                        2
                    } else {
                        1
                    }
                )
            }
        """.trimIndent()
        val expectedPython = """
            def f(n):
                while True:
                    if n == 1:
                        anonymous = 2
                    else:
                        anonymous = 1
                    n_1 = anonymous
                    n = n_1
        """.trimIndent()
        val shed = parse("<string>", shedSource)
        val intBuiltin = builtinType("Int", IntType)
        val references = resolve(shed, globals = mapOf(Identifier("Int") to intBuiltin))
        val node = generateCode(shed, references = references)

        assertThat(serialise(node).trim(), equalTo(expectedPython))
    }

    @Test
    fun partialFunctionCallGeneratesPartialFunctionCall() {
        val declaration = parameter("f")
        val function = variableReference("f")
        val shed = partialCall(
            function,
            positionalArguments = listOf(literalInt(42)),
            namedArguments = listOf(callNamedArgument("x", literalBool(true)))
        )

        val node = generateExpressionCode(shed, context(references = mapOf(function to declaration)))

        assertThat(node, isGeneratedExpression(isPythonFunctionCall(
            isPythonVariableReference("_partial"),
            isSequence(isPythonVariableReference("f"), isPythonIntegerLiteral(42)),
            isSequence(isPair(equalTo("x"), isPythonBooleanLiteral(true)))
        )))
    }

    @Test
    fun fieldAccessGeneratesAttributeAccess() {
        val declaration = parameter("x")
        val receiver = variableReference("x")
        val shed = fieldAccess(receiver, "y")

        val node = generateExpressionCode(shed, context(references = mapOf(receiver to declaration)))

        assertThat(node, isGeneratedExpression(isPythonAttributeAccess(
            receiver = isPythonVariableReference("x"),
            attributeName = equalTo("y")
        )))
    }

    @Test
    fun fieldAccessFieldNamesArePythonised() {
        val declaration = parameter("x")
        val receiver = variableReference("x")
        val shed = fieldAccess(receiver, "someValue")

        val node = generateExpressionCode(shed, context(references = mapOf(receiver to declaration)))

        assertThat(node, isGeneratedExpression(isPythonAttributeAccess(
            receiver = isPythonVariableReference("x"),
            attributeName = equalTo("some_value")
        )))
    }

    @Test
    fun staticFieldAccessGeneratesAttributeAccess() {
        val declaration = parameter("x")
        val receiver = staticReference("x")
        val shed = staticFieldAccess(receiver, "y")

        val node = generateCode(shed, context(references = mapOf(receiver to declaration)))

        assertThat(node, isPythonAttributeAccess(
            receiver = isPythonVariableReference("x"),
            attributeName = equalTo("y")
        ))
    }

    @Test
    fun staticFieldAccessFieldNamesArePythonised() {
        val declaration = parameter("x")
        val receiver = staticReference("x")
        val shed = staticFieldAccess(receiver, "someValue")

        val node = generateCode(shed, context(references = mapOf(receiver to declaration)))

        assertThat(node, isPythonAttributeAccess(
            receiver = isPythonVariableReference("x"),
            attributeName = equalTo("some_value")
        ))
    }

    private fun generateCode(node: ModuleNode, references: ResolvedReferences): PythonModuleNode {
        return generateCode(
            moduleName = listOf(),
            moduleSet = ModuleSet(listOf()),
            node = node,
            references = references,
            types = EMPTY_TYPES
        )
    }

    private fun generateCode(node: ModuleNode) = generateCode(node, context())
    private fun generateCodeForModuleStatement(node: ModuleStatementNode) = generateModuleStatementCode(node, context())
    private fun generateCodeForFunctionStatement(node: FunctionStatementNode): List<PythonStatementNode> {
        val context = context()
        return generateCodeForFunctionStatement(
            node,
            context,
            returnValue = { expression, source ->
                generateExpressionCode(expression, context).toStatements { pythonExpression ->
                    listOf(PythonReturnNode(pythonExpression, source))
                }
            }
        )
    }
    private fun generateCode(node: ExpressionNode) = generateExpressionCode(node, context())

    private fun context(
        isPackage: Boolean = false,
        moduleName: List<String> = listOf(),
        references: Map<ReferenceNode, VariableBindingNode> = mapOf(),
        types: Types = EMPTY_TYPES
    ) = CodeGenerationContext(
        isPackage = isPackage,
        moduleName = moduleName.map(::Identifier),
        references = ResolvedReferencesMap(references.entries.associate({ entry -> entry.key.nodeId to entry.value })),
        types = types
    )

    private fun isPythonModule(body: Matcher<List<PythonStatementNode>>)
        = cast(has(PythonModuleNode::body, body))

    private fun isPythonImportFrom(
        module: Matcher<String>,
        names: Matcher<List<Pair<String, String>>>
    ) = cast(allOf(
        has(PythonImportFromNode::module, module),
        has(PythonImportFromNode::names, names)
    ))

    private fun isPythonClass(
        name: Matcher<String>,
        body: Matcher<List<PythonStatementNode>> = anything
    ) : Matcher<PythonStatementNode>
        = cast(allOf(
        has(PythonClassNode::name, name),
        has(PythonClassNode::body, body)
    ))

    private fun isPythonFunction(
        name: Matcher<String> = anything,
        parameters: Matcher<List<String>> = anything,
        body: Matcher<List<PythonStatementNode>> = anything
    ) : Matcher<PythonStatementNode>
        = cast(allOf(
            has(PythonFunctionNode::name, name),
            has(PythonFunctionNode::parameters, parameters),
            has(PythonFunctionNode::body, body)
        ))

    private fun isPythonLambda(
        parameters: Matcher<List<String>> = anything,
        body: Matcher<PythonExpressionNode> = anything
    ): Matcher<PythonExpressionNode> = cast(allOf(
        has(PythonLambdaNode::parameters, parameters),
        has(PythonLambdaNode::body, body)
    ))

    private fun isPythonIfStatement(
        conditionalBranches: Matcher<List<PythonConditionalBranchNode>>,
        elseBranch: Matcher<List<PythonStatementNode>>
    ) : Matcher<PythonStatementNode> = cast(allOf(
        has(PythonIfStatementNode::conditionalBranches, conditionalBranches),
        has(PythonIfStatementNode::elseBranch, elseBranch)
    ))

    private fun isPythonConditionalBranch(
        condition: Matcher<PythonExpressionNode> = anything,
        body: Matcher<List<PythonStatementNode>>
    ) : Matcher<PythonConditionalBranchNode> = allOf(
        has(PythonConditionalBranchNode::condition, condition),
        has(PythonConditionalBranchNode::body, body)
    )

    private fun isPythonReturn(expression: Matcher<PythonExpressionNode>)
        : Matcher<PythonStatementNode>
        = cast(has(PythonReturnNode::expression, expression))

    private fun isPythonExpressionStatement(expression: Matcher<PythonExpressionNode>)
        : Matcher<PythonStatementNode>
        = cast(has(PythonExpressionStatementNode::expression, expression))

    private fun isPythonAssignment(
        target: String,
        expression: Matcher<PythonExpressionNode>
    ) = isPythonAssignment(
        target = isPythonVariableReference(target),
        expression = expression
    )

    private fun isPythonAssignment(
        target: Matcher<PythonExpressionNode>,
        expression: Matcher<PythonExpressionNode> = anything
    ): Matcher<PythonStatementNode> {
        return cast(allOf(
            has(PythonAssignmentNode::target, target),
            has(PythonAssignmentNode::expression, expression)
        ))
    }

    private fun isPythonNone()
        : Matcher<PythonExpressionNode>
        = isA<PythonNoneLiteralNode>()

    private fun isPythonBooleanLiteral(value: Boolean)
        : Matcher<PythonExpressionNode>
        = cast(has(PythonBooleanLiteralNode::value, equalTo(value)))

    private fun isPythonIntegerLiteral(value: Int)
        : Matcher<PythonExpressionNode>
        = cast(has(PythonIntegerLiteralNode::value, has(BigInteger::intValueExact, equalTo(value))))

    private fun isPythonStringLiteral(value: String)
        : Matcher<PythonExpressionNode>
        = cast(has(PythonStringLiteralNode::value, equalTo(value)))

    private fun isPythonVariableReference(name: String)
        : Matcher<PythonExpressionNode>
        = cast(has(PythonVariableReferenceNode::name, equalTo(name)))

    private fun isPythonTuple(elements: Matcher<List<PythonExpressionNode>>)
        : Matcher<PythonExpressionNode>
        = cast(has(PythonTupleNode::members, elements))

    private fun isPythonUnaryOperation(
        operator: Matcher<PythonUnaryOperator>,
        operand: Matcher<PythonExpressionNode>
    ): Matcher<PythonExpressionNode> = cast(allOf(
        has(PythonUnaryOperationNode::operator, operator),
        has(PythonUnaryOperationNode::operand, operand)
    ))

    private fun isPythonBinaryOperation(
        operator: Matcher<PythonBinaryOperator>,
        left: Matcher<PythonExpressionNode>,
        right: Matcher<PythonExpressionNode>
    ) : Matcher<PythonExpressionNode>
    = cast(allOf(
        has(PythonBinaryOperationNode::operator, operator),
        has(PythonBinaryOperationNode::left, left),
        has(PythonBinaryOperationNode::right, right)
    ))

    private fun isPythonFunctionCall(
        function: Matcher<PythonExpressionNode>,
        arguments: Matcher<List<PythonExpressionNode>> = isSequence(),
        keywordArguments: Matcher<List<Pair<String, PythonExpressionNode>>> = isSequence()
    ) : Matcher<PythonExpressionNode>
    = cast(allOf(
        has(PythonFunctionCallNode::function, function),
        has(PythonFunctionCallNode::arguments, arguments),
        has(PythonFunctionCallNode::keywordArguments, keywordArguments)
    ))

    private fun isPythonAttributeAccess(
        receiver: Matcher<PythonExpressionNode>,
        attributeName: Matcher<String>
    ) : Matcher<PythonExpressionNode>
        = cast(allOf(
        has(PythonAttributeAccessNode::receiver, receiver),
        has(PythonAttributeAccessNode::attributeName, attributeName)
    ))

    private fun isPythonTypeCondition(
        expression: Matcher<PythonExpressionNode>,
        discriminator: Discriminator
    ): Matcher<PythonExpressionNode> {
        val symbol = discriminator.symbolType.symbol
        return isPythonBinaryOperation(
            operator = equalTo(PythonBinaryOperator.EQUALS),
            left = isPythonAttributeAccess(
                receiver = expression,
                attributeName = equalTo(discriminator.fieldName.value)
            ),
            right = isPythonStringLiteral(symbol.fullName)
        )
    }

    private fun isGeneratedExpression(value: Matcher<PythonExpressionNode>) = allOf(
        has(GeneratedCode<PythonExpressionNode>::value, value),
        has(GeneratedCode<PythonExpressionNode>::statements, isEmpty)
    )

    private fun serialise(statements: List<PythonStatementNode>): String {
        return statements.map { statement ->
            serialise(statement, indentation = 0)
        }.joinToString("\n")
    }
}
