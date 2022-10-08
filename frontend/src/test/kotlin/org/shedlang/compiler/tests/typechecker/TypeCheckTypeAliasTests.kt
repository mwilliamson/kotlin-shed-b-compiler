package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.typeCheckModuleStatement
import org.shedlang.compiler.types.IntMetaType

class TypeCheckTypeAliasTests {
    @Test
    fun typeAliasDeclaresType() {
        val intType = typeLevelReference("Int")
        val node = typeAliasDeclaration("Size", expression = intType)

        val typeContext = typeContext(referenceTypes = mapOf(
            intType to IntMetaType
        ))
        typeCheckModuleStatement(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isTypeAlias(
            name = isIdentifier("Size"),
            aliasedType = isIntType
        )))
    }
}
