package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.IntMetaType
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.StaticValueType
import org.shedlang.compiler.types.MetaTypeGroup

class TypeCheckTypesModuleTests {
    @Test
    fun bodyIsTypeChecked() {
        val reference = staticReference("x")
        val node = typesModule(body = listOf(
            valType(type = reference)
        ))

        val typeContext = typeContext(referenceTypes = mapOf(reference to IntType))

        assertThat(
            {
                typeCheck(moduleName(), node, typeContext)
            },
            throwsUnexpectedType(expected = MetaTypeGroup, actual = IntType)
        )
    }

    @Test
    fun typeOfModuleIsReturned() {
        val intReference = staticReference("Int")
        val node = typesModule(
            body = listOf(
                valType(name = "value", type = intReference)
            )
        )

        val result = typeCheck(moduleName(), node, typeContext(
            referenceTypes = mapOf(intReference to IntMetaType)
        ))
        assertThat(result.fields, isMap(
            Identifier("value") to isIntType
        ))
    }
}
