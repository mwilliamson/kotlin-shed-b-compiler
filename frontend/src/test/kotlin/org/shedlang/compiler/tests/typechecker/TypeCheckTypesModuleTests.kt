package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.frontend.tests.isIntType
import org.shedlang.compiler.tests.isMap
import org.shedlang.compiler.tests.staticReference
import org.shedlang.compiler.tests.typesModule
import org.shedlang.compiler.tests.valType
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.MetaType
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
                typeCheck(node, typeContext)
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

        val result = typeCheck(node, typeContext(
            referenceTypes = mapOf(intReference to MetaType(IntType))
        ))
        assertThat(result.fields, isMap(
            Identifier("value") to isIntType
        ))
    }
}
