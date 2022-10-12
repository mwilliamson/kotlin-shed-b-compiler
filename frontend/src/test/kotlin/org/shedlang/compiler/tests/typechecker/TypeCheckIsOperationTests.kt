package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.CouldNotFindDiscriminator
import org.shedlang.compiler.typechecker.inferType
import org.shedlang.compiler.typechecker.typeCheckExpression
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.metaType

class TypeCheckIsOperationTests {
    @Test
    fun expressionMustBeUnion() {
        val memberType = shapeType(name = "Member")
        val memberReference = typeLevelReference("Member")

        val expression = isOperation(expression = literalInt(1), type = memberReference)
        val typeContext = typeContext(referenceTypes = mapOf(memberReference to metaType(memberType)))
        assertThat(
            { typeCheckExpression(expression, typeContext) },
            throwsUnexpectedType(expected = isUnionTypeGroup, actual = IntType)
        )
    }

    @Test
    fun whenDiscriminatorCannotBeFoundThenErrorIsRaised() {
        val memberType = shapeType(name = "Member")
        val unionType = unionType(name = "Union", members = listOf(memberType))

        val memberReference = typeLevelReference("Member")
        val valueReference = variableReference("value")
        val expression = isOperation(expression = valueReference, type = memberReference)
        val typeContext = typeContext(
            referenceTypes = mapOf(
                memberReference to metaType(memberType),
                valueReference to unionType
            )
        )
        assertThat(
            { typeCheckExpression(expression, typeContext) },
            throws(allOf(
                has(CouldNotFindDiscriminator::sourceType, isType(unionType)),
                has(CouldNotFindDiscriminator::targetType, cast(isType(memberType)))
            ))
        )
    }

    @Test
    fun isOperationHasBooleanType() {
        val tag = tag(listOf("Example"), "Union")
        val memberType = shapeType(name = "Member", tagValue = tagValue(tag, "B"))
        val unionType = unionType(name = "Union", tag = tag, members = listOf(memberType))

        val memberReference = typeLevelReference("Member")
        val valueReference = variableReference("value")
        val expression = isOperation(expression = valueReference, type = memberReference)
        val typeContext = typeContext(
            referenceTypes = mapOf(
                memberReference to metaType(memberType),
                valueReference to unionType
            )
        )
        val type = inferType(expression, typeContext)
        assertThat(type, isBoolType)
    }

    @Test
    fun discriminatorIsStored() {
        val tag = tag(listOf("Example"), "Union")
        val tagValue = tagValue(tag, "B")
        val memberType = shapeType(name = "Member", tagValue = tagValue)
        val unionType = unionType(name = "Union", tag = tag, members = listOf(memberType))

        val memberReference = typeLevelReference("Member")
        val valueReference = variableReference("value")
        val expression = isOperation(expression = valueReference, type = memberReference)
        val typeContext = typeContext(
            referenceTypes = mapOf(
                memberReference to metaType(memberType),
                valueReference to unionType
            )
        )

        inferType(expression, typeContext)

        assertThat(
            typeContext.toTypes().discriminatorForIsExpression(expression),
            isDiscriminator(
                tagValue = equalTo(tagValue)
            )
        )
    }
}
