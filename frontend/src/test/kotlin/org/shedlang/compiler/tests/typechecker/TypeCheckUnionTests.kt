package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.BoolType
import org.shedlang.compiler.typechecker.IntType
import org.shedlang.compiler.typechecker.MetaType
import org.shedlang.compiler.typechecker.typeCheck

class TypeCheckUnionTests {
    @Test
    fun unionDeclaresType() {
        val intType = typeReference("Int")
        val boolType = typeReference("Bool")
        val node = union("X", listOf(
            intType,
            boolType
        ))

        val typeContext = typeContext(referenceTypes = mapOf(
            intType to MetaType(IntType),
            boolType to MetaType(BoolType)
        ))
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isUnionType(
            name = equalTo("X"),
            members = isSequence(isIntType, isBoolType)
        )))
    }
}
