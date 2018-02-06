package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.inferType
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.*

class TypeCheckIfStatementTests {
    @Test
    fun whenConditionIsNotBooleanThenIfStatementDoesNotTypeCheck() {
        val statement = ifStatement(condition = literalInt(1))
        assertThat(
            { typeCheck(statement, emptyTypeContext()) },
            throwsUnexpectedType(expected = BoolType, actual = IntType)
        )
    }

    @Test
    fun trueBranchIsTypeChecked() {
        assertStatementInStatementIsTypeChecked { badStatement -> ifStatement(trueBranch = listOf(badStatement)) }
    }

    @Test
    fun elseBranchIsTypeChecked() {
        assertStatementInStatementIsTypeChecked { badStatement -> ifStatement(elseBranch = listOf(badStatement)) }
    }

    @Test
    fun typeOfIfNodeIsUnionOfBranchTypes() {
        val tag = TagField("Tag")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tag, freshNodeId()))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tag, freshNodeId()))
        val reference1 = variableReference("member1")
        val reference2 = variableReference("member2")

        val expression = ifExpression(
            trueBranch = listOf(expressionStatement(reference1, isReturn = true)),
            elseBranch = listOf(expressionStatement(reference2, isReturn = true))
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

    // TODO: Test that refined type is only in true branch (not false branch, nor following statements)
    @Test
    fun whenConditionIsIsOperationThenTypeIsRefinedInTrueBranch() {
        val argument = argument("x")

        val variableReference = variableReference("x")
        val receiverReference = variableReference("f")

        val member1Reference = staticReference("Member1")
        val tagField = TagField("Tag")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tagField, freshNodeId()))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tagField, freshNodeId()))
        val union = unionType("Union", members = listOf(member1, member2), tagField = tagField)
        val statement = ifStatement(
            condition = isOperation(variableReference, member1Reference),
            trueBranch = listOf(
                expressionStatement(call(receiverReference, listOf(variableReference))),
                expressionStatement(literalUnit())
            )
        )

        val typeContext = typeContext(
            returnType = member1,
            referenceTypes = mapOf(
                member1Reference to MetaType(member1),
                receiverReference to functionType(positionalArguments = listOf(member1))
            ),
            references = mapOf(
                variableReference to argument
            ),
            types = mapOf(
                argument to union
            )
        )

        typeCheck(statement, typeContext)
    }
}
