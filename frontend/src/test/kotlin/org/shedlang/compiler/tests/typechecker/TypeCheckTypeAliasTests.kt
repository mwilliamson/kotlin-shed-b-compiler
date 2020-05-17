package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.typeCheckModuleStatement
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.StaticValueType

class TypeCheckTypeAliasTests {
    @Test
    fun typeAliasDeclaresType() {
        val intType = staticReference("Int")
        val node = typeAliasDeclaration("Size", expression = intType)

        val typeContext = typeContext(referenceTypes = mapOf(
            intType to StaticValueType(IntType)
        ))
        typeCheckModuleStatement(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isTypeAlias(
            name = isIdentifier("Size"),
            aliasedType = isIntType
        )))
    }
}
