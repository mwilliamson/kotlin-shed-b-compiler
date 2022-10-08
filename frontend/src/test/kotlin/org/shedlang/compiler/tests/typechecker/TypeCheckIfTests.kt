package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.inferType
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.BoolType
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.metaType

class TypeCheckIfTests {
    @Test
    fun whenConditionIsNotBooleanThenIfExpressionDoesNotTypeCheck() {
        val statement = ifExpression(condition = literalInt(1))
        assertThat(
            { typeCheck(statement, emptyTypeContext()) },
            throwsUnexpectedType(expected = BoolType, actual = IntType)
        )
    }

    @Test
    fun conditionalBranchIsTypeChecked() {
        assertStatementIsTypeChecked { badStatement -> typeCheck(
            ifExpression(trueBranch = listOf(badStatement)),
            typeContext()
        ) }
    }

    @Test
    fun elseBranchIsTypeChecked() {
        assertStatementIsTypeChecked { badStatement -> typeCheck(
            ifExpression(elseBranch = listOf(badStatement)),
            typeContext()
        ) }
    }

    @Test
    fun typeOfIfNodeIsUnionOfBranchTypes() {
        val tag = tag(listOf("Example"), "X")
        val member1 = shapeType(name = "Member1", tagValue = tagValue(tag, "Member1"))
        val member2 = shapeType(name = "Member2", tagValue = tagValue(tag, "Member2"))
        val reference1 = variableReference("member1")
        val reference2 = variableReference("member2")

        val expression = ifExpression(
            trueBranch = listOf(expressionStatementReturn(reference1)),
            elseBranch = listOf(expressionStatementReturn(reference2))
        )
        val context = typeContext(
            referenceTypes = mapOf(
                reference1 to member1,
                reference2 to member2
            )
        )
        assertThat(inferType(expression, context), isUnionType(members = isSequence(
            cast(equalTo(member1)),
            cast(equalTo(member2))
        )))
    }

    @Test
    fun whenConditionIsIsOperationThenTypeIsRefinedInTrueBranch() {
        val declaration = variableBinder("x")
        val variableReference = variableReference("x")
        val referenceInTrueBranch = variableReference("x")
        val referenceInFalseBranch = variableReference("x")

        val tag = tag(listOf("Example"), "Union")
        val member1Reference = typeLevelReference("Member1")
        val member1 = shapeType(name = "Member1", tagValue = tagValue(tag, "Member1"))
        val member2 = shapeType(name = "Member2", tagValue = tagValue(tag, "Member2"))
        val union = unionType("Union", tag = tag, members = listOf(member1, member2))

        val types = captureTypes(
            ifExpression(
                condition = isOperation(variableReference, member1Reference),
                trueBranch = listOf(
                    expressionStatement(referenceInTrueBranch)
                ),
                elseBranch = listOf(
                    expressionStatement(referenceInFalseBranch)
                )
            ),
            referenceTypes = mapOf(
                member1Reference to metaType(member1)
            ),
            references = mapOf(
                variableReference to declaration,
                referenceInTrueBranch to declaration,
                referenceInFalseBranch to declaration
            ),
            types = mapOf(
                declaration to union
            )
        )

        assertThat(types.typeOfExpression(referenceInTrueBranch), isType(member1))
        assertThat(types.typeOfExpression(referenceInFalseBranch), isType(union))
    }
}
