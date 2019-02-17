package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.CouldNotFindDiscriminator
import org.shedlang.compiler.typechecker.WhenIsNotExhaustiveError
import org.shedlang.compiler.typechecker.inferType
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.MetaType
import org.shedlang.compiler.types.freshShapeId

class TypeCheckWhenTests {
    private val shapeId = freshShapeId();
    private val inputMember1TypeReference = staticReference("Member1")
    private val inputMember2TypeReference = staticReference("Member2")
    private val inputMember1 = shapeType(name = "Member1", fields = listOf(
        field(name = "tag", type = symbolType(listOf(), "@Member1"), shapeId = shapeId)
    ))
    private val inputMember2 = shapeType(name = "Member2", fields = listOf(
        field(name = "tag", type = symbolType(listOf(), "@Member2"), shapeId = shapeId)
    ))
    private val inputUnion = unionType("Union", members = listOf(inputMember1, inputMember2))

    private val outputMember1Reference = variableReference("outputMember1")
    private val outputMember2Reference = variableReference("outputMember2")
    private val outputMember1 = shapeType(name = "OutputMember1")
    private val outputMember2 = shapeType(name = "OutputMember2")

    @Test
    fun expressionMustBeUnion() {
        val expression = whenExpression(expression = literalInt(1))
        assertThat(
            { typeCheck(expression, typeContext()) },
            throwsUnexpectedType(expected = isUnionTypeGroup, actual = IntType)
        )
    }

    @Test
    fun whenDiscriminatorCannotBeFoundThenErrorIsRaised() {
        val memberType = shapeType(name = "Member")
        val unionType = unionType(name = "Union", members = listOf(memberType))

        val memberReference = staticReference("Member")
        val valueReference = variableReference("value")
        val expression = whenExpression(
            expression = valueReference,
            branches = listOf(
                whenBranch(type = memberReference)
            )
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(
                memberReference to MetaType(memberType),
                valueReference to unionType
            )
        )
        assertThat(
            { typeCheck(expression, typeContext) },
            throws(allOf(
                has(CouldNotFindDiscriminator::sourceType, isType(unionType)),
                has(CouldNotFindDiscriminator::targetType, isType(memberType))
            ))
        )
    }

    @Test
    fun typeIsUnionOfBranchTypes() {
        val variableReference = variableReference("x")

        val expression = whenExpression(
            expression = variableReference,
            branches = listOf(
                whenBranch(
                    type = inputMember1TypeReference,
                    body = listOf(
                        expressionStatement(outputMember1Reference, isReturn = true)
                    )
                ),
                whenBranch(
                    type = inputMember2TypeReference,
                    body = listOf(
                        expressionStatement(outputMember2Reference, isReturn = true)
                    )
                )
            )
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(
                variableReference to inputUnion,
                inputMember1TypeReference to MetaType(inputMember1),
                inputMember2TypeReference to MetaType(inputMember2),
                outputMember1Reference to outputMember1,
                outputMember2Reference to outputMember2
            )
        )
        val type = inferType(expression, typeContext)

        assertThat(type, isUnionType(members = isSequence(isType(outputMember1), isType(outputMember2))))
    }

    @Test
    fun whenElseBranchIsPresentThenUnionOfTypesIncludesElse() {
        val variableReference = variableReference("x")

        val expression = whenExpression(
            expression = variableReference,
            branches = listOf(
                whenBranch(
                    type = inputMember1TypeReference,
                    body = listOf(
                        expressionStatement(outputMember1Reference, isReturn = true)
                    )
                )
            ),
            elseBranch = listOf(
                expressionStatement(outputMember2Reference, isReturn = true)
            )
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(
                variableReference to inputUnion,
                inputMember1TypeReference to MetaType(inputMember1),
                inputMember2TypeReference to MetaType(inputMember2),
                outputMember1Reference to outputMember1,
                outputMember2Reference to outputMember2
            )
        )
        val type = inferType(expression, typeContext)

        assertThat(type, isUnionType(members = isSequence(isType(outputMember1), isType(outputMember2))))
    }

    @Test
    fun typeIsRefinedInBranchBodies() {
        val declaration = variableBinder("x")
        val variableReference = variableReference("x")
        val refinedVariableReference = variableReference("x")

        val types = captureTypes(
            whenExpression(
                expression = variableReference,
                branches = listOf(
                    whenBranch(
                        type = inputMember1TypeReference,
                        body = listOf(
                            expressionStatement(refinedVariableReference)
                        )
                    ),
                    whenBranch(type = inputMember2TypeReference)
                )
            ),
            references = mapOf(
                variableReference to declaration,
                refinedVariableReference to declaration
            ),
            referenceTypes = mapOf(
                inputMember1TypeReference to MetaType(inputMember1),
                inputMember2TypeReference to MetaType(inputMember2)
            ),
            types = mapOf(declaration to inputUnion)
        )

        assertThat(types.typeOf(refinedVariableReference), isType(inputMember1))
    }

    @Test
    fun errorIsThrownWhenCasesAreNotExhaustive() {
        val variableReference = variableReference("x")

        val statement = whenExpression(
            expression = variableReference,
            branches = listOf(
                whenBranch(
                    type = inputMember1TypeReference,
                    body = listOf()
                )
            )
        )

        val typeContext = typeContext(
            referenceTypes = mapOf(
                variableReference to inputUnion,
                inputMember1TypeReference to MetaType(inputMember1)
            )
        )

        assertThat(
            { typeCheck(statement, typeContext) },
            throws<WhenIsNotExhaustiveError>(has(
                WhenIsNotExhaustiveError::unhandledMembers,
                isSequence(isType(inputMember2))
            ))
        )
    }
}
