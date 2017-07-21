package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.isBoolType
import org.shedlang.compiler.tests.isIntType
import org.shedlang.compiler.tests.isShapeType
import org.shedlang.compiler.tests.shapeType
import org.shedlang.compiler.types.*

class TypeApplicationTests {
    @Test
    fun applyingTypeToShapeRenamesShape() {
        val typeParameter1 = TypeParameter("T")
        val typeParameter2 = TypeParameter("U")
        val listType = TypeFunction(
            listOf(typeParameter1, typeParameter2),
            shapeType("Pair", fields = mapOf())
        )
        assertThat(
            applyType(listType, listOf(BoolType, IntType)),
            isShapeType(name = equalTo("Pair[Bool, Int]"), fields = listOf())
        )
    }

    @Test
    fun applyingTypeToShapeReplacesTypeParameters() {
        val typeParameter1 = TypeParameter("T")
        val typeParameter2 = TypeParameter("U")
        val listType = TypeFunction(
            listOf(typeParameter1, typeParameter2),
            shapeType("Pair", fields = mapOf(
                "first" to typeParameter1,
                "second" to typeParameter2
            ))
        )
        assertThat(
            applyType(listType, listOf(BoolType, IntType)),
            isShapeType(fields = listOf(
                "first" to isBoolType,
                "second" to isIntType
            ))
        )
    }
}
