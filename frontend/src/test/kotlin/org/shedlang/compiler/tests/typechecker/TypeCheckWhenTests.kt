package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.inferType
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.*

class TypeCheckWhenTests {
    private val inputMember1TypeReference = staticReference("Member1")
    private val inputMember2TypeReference = staticReference("Member2")
    private val inputTagField = TagField("Tag")
    private val inputMember1 = shapeType(name = "Member1", tagValue = TagValue(inputTagField, freshNodeId()))
    private val inputMember2 = shapeType(name = "Member2", tagValue = TagValue(inputTagField, freshNodeId()))
    private val inputUnion = unionType("Union", members = listOf(inputMember1, inputMember2), tagField = inputTagField)

    private val outputMember1Reference = variableReference("outputMember1")
    private val outputMember2Reference = variableReference("outputMember2")
    private val outputTagField = TagField("OutputTag")
    private val outputMember1 = shapeType(name = "OutputMember1", tagValue = TagValue(outputTagField, freshNodeId()))
    private val outputMember2 = shapeType(name = "OutputMember2", tagValue = TagValue(outputTagField, freshNodeId()))

    @Test
    fun expressionMustBeUnion() {
        val expression = whenExpression(expression = literalInt(1))
        assertThat(
            { typeCheck(expression, typeContext()) },
            throwsUnexpectedType(expected = isUnionTypeGroup, actual = IntType)
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
    fun typeIsRefinedInBranchBodies() {
        val variableReference = variableReference("x")
        val receiverReference = variableReference("f")

        val statement = whenExpression(
            expression = variableReference,
            branches = listOf(
                whenBranch(
                    type = inputMember1TypeReference,
                    body = listOf(
                        expressionStatement(call(receiverReference, listOf(variableReference))),
                        expressionStatement(literalUnit())
                    )
                )
            )
        )

        val typeContext = typeContext(
            referenceTypes = mapOf(
                variableReference to inputUnion,
                inputMember1TypeReference to MetaType(inputMember1),
                receiverReference to functionType(positionalParameters = listOf(inputMember1))
            )
        )

        typeCheck(statement, typeContext)
    }
}
