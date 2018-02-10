package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.inferType
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.MetaType
import org.shedlang.compiler.types.TagField
import org.shedlang.compiler.types.TagValue

class TypeCheckIsOperationTests {
    @Test
    fun expressionMustBeUnion() {
        val tagField = TagField("Tag")
        val memberType = shapeType(name = "Member", tagValue = TagValue(tagField, 0))
        val memberReference = staticReference("Member")

        val expression = isOperation(expression = literalInt(1), type = memberReference)
        val typeContext = typeContext(referenceTypes = mapOf(memberReference to MetaType(memberType)))
        assertThat(
            { typeCheck(expression, typeContext) },
            throwsUnexpectedType(expected = isUnionTypeGroup, actual = IntType)
        )
    }

    @Test
    fun isOperationHasBooleanType() {
        val tagField = TagField("Tag")
        val memberType = shapeType(name = "Member", tagValue = TagValue(tagField, 0))
        val unionType = unionType(name = "Union", members = listOf(memberType), tagField = tagField)

        val memberReference = staticReference("Member")
        val valueReference = variableReference("value")
        val expression = isOperation(expression = valueReference, type = memberReference)
        val typeContext = typeContext(
            referenceTypes = mapOf(
                memberReference to MetaType(memberType),
                valueReference to unionType
            )
        )
        val type = inferType(expression, typeContext)
        assertThat(type, isBoolType)
    }
}
