package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.typechecker.MetaType
import org.shedlang.compiler.typechecker.UnitType
import org.shedlang.compiler.typechecker.UnresolvedReferenceError
import org.shedlang.compiler.typechecker.typeCheck

class TypeCheckModuleTests {
    @Test
    fun bodyIsTypeChecked() {
        val node = module(body = listOf(
            function(returnType = typeReference("X"))
        ))

        assertThat(
            { typeCheck(node, emptyTypeContext() )},
            throws(cast(has(UnresolvedReferenceError::name, equalTo("X"))))
        )
    }

    @Test
    fun functionsCanCallEachOtherRecursively() {
        val node = module(body = listOf(
            function(name = "f", body = listOf(
                expressionStatement(functionCall(variableReference("g"), listOf()))
            )),
            function(name = "g", body = listOf(
                expressionStatement(functionCall(variableReference("f"), listOf()))
            ))
        ))

        typeCheck(node, typeContext(variables = mapOf("Unit" to MetaType(UnitType))))
    }
}
