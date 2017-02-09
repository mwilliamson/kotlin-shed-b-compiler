package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.BoolType
import org.shedlang.compiler.typechecker.IntType
import org.shedlang.compiler.typechecker.MetaType
import org.shedlang.compiler.typechecker.inferType

class TypeCheckShapeTests {
    @Test
    fun shapeDeclaresType() {
        val intType = typeReference("Int")
        val boolType = typeReference("Bool")
        val node = shape("X", listOf(
            shapeField("a", intType),
            shapeField("b", boolType)
        ))


        val type = inferType(
            node,
            typeContext(referenceTypes = mapOf(
                intType to MetaType(IntType),
                boolType to MetaType(BoolType)
            ))
        )
        assertThat(type, isMetaType(isShapeType(
            name = equalTo("X"),
            fields = listOf("a" to isIntType, "b" to isBoolType)
        )))
    }
}
