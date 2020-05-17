package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.*
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.StaticValueType

class TypeCheckWhenTests {
    private val inputTag = tag(listOf("Example"), "Union")
    private val inputMember1TypeReference = staticReference("Member1")
    private val inputMember2TypeReference = staticReference("Member2")
    private val inputTagValue1 = tagValue(inputTag, "Member1")
    private val inputMember1 = shapeType(name = "Member1", tagValue = inputTagValue1)
    private val inputTagValue2 = tagValue(inputTag, "Member2")
    private val inputMember2 = shapeType(name = "Member2", tagValue = inputTagValue2)
    private val inputUnion = unionType("Union", tag = inputTag, members = listOf(inputMember1, inputMember2))

    private val outputTag = tag(listOf("Example"), "Output")
    private val outputMember1Reference = variableReference("outputMember1")
    private val outputMember2Reference = variableReference("outputMember2")
    private val outputMember1 = shapeType(name = "OutputMember1", tagValue = tagValue(outputTag, "OutputMember1"))
    private val outputMember2 = shapeType(name = "OutputMember2", tagValue = tagValue(outputTag, "OutputMember2"))

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
                memberReference to StaticValueType(memberType),
                valueReference to unionType
            )
        )
        assertThat(
            { typeCheck(expression, typeContext) },
            throws(allOf(
                has(CouldNotFindDiscriminator::sourceType, isType(unionType)),
                has(CouldNotFindDiscriminator::targetType, cast(isType(memberType)))
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
                        expressionStatementReturn(outputMember1Reference)
                    )
                ),
                whenBranch(
                    type = inputMember2TypeReference,
                    body = listOf(
                        expressionStatementReturn(outputMember2Reference)
                    )
                )
            )
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(
                variableReference to inputUnion,
                inputMember1TypeReference to StaticValueType(inputMember1),
                inputMember2TypeReference to StaticValueType(inputMember2),
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
                        expressionStatementReturn(outputMember1Reference)
                    )
                )
            ),
            elseBranch = listOf(
                expressionStatementReturn(outputMember2Reference)
            )
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(
                variableReference to inputUnion,
                inputMember1TypeReference to StaticValueType(inputMember1),
                inputMember2TypeReference to StaticValueType(inputMember2),
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
                inputMember1TypeReference to StaticValueType(inputMember1),
                inputMember2TypeReference to StaticValueType(inputMember2)
            ),
            types = mapOf(declaration to inputUnion)
        )

        assertThat(types.typeOfExpression(refinedVariableReference), isType(inputMember1))
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
                inputMember1TypeReference to StaticValueType(inputMember1)
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

    @Test
    fun errorIsThrownWhenElseBranchIsNotReachable() {
        val variableReference = variableReference("x")

        val expression = whenExpression(
            expression = variableReference,
            branches = listOf(
                whenBranch(
                    type = inputMember1TypeReference,
                    body = listOf()
                ),
                whenBranch(
                    type = inputMember2TypeReference,
                    body = listOf()
                )
            ),
            elseBranch = listOf()
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(
                variableReference to inputUnion,
                inputMember1TypeReference to StaticValueType(inputMember1),
                inputMember2TypeReference to StaticValueType(inputMember2)
            )
        )

        assertThat(
            { typeCheck(expression, typeContext) },
            throws<WhenElseIsNotReachableError>()
        )
    }

    @Test
    fun discriminatorIsStoredForEachBranch() {
        val variableReference = variableReference("x")

        val branch1 = whenBranch(
            type = inputMember1TypeReference,
            body = listOf()
        )
        val branch2 = whenBranch(
            type = inputMember2TypeReference,
            body = listOf()
        )
        val expression = whenExpression(
            expression = variableReference,
            branches = listOf(
                branch1,
                branch2
            )
        )
        val typeContext = typeContext(
            referenceTypes = mapOf(
                variableReference to inputUnion,
                inputMember1TypeReference to StaticValueType(inputMember1),
                inputMember2TypeReference to StaticValueType(inputMember2)
            )
        )
        inferType(expression, typeContext)

        assertThat(
            typeContext.toTypes().discriminatorForWhenBranch(branch1),
            isDiscriminator(
                tagValue = equalTo(inputTagValue1)
            )
        )
        assertThat(
            typeContext.toTypes().discriminatorForWhenBranch(branch2),
            isDiscriminator(
                tagValue = equalTo(inputTagValue2)
            )
        )
    }
}
