package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.functionCall
import org.shedlang.compiler.tests.literalInt
import org.shedlang.compiler.tests.valStatement
import org.shedlang.compiler.tests.variableReference
import org.shedlang.compiler.typechecker.*

class TypeCheckValTests {
    @Test
    fun expressionIsTypeChecked() {
        val functionReference = variableReference("f")
        val node = valStatement(name = "x", expression = functionCall(functionReference))
        assertThat(
            { typeCheck(node, typeContext(referenceTypes = mapOf(functionReference to UnitType))) },
            throws(has(UnexpectedTypeError::actual, equalTo<Type>(UnitType)))
        )
    }

    @Test
    fun valTakesTypeOfExpression() {
        val node = valStatement(name = "x", expression = literalInt())
        val variables = mutableMapOf<Int, Type>()
        val typeContext = TypeContext(
            returnType = null,
            variables = variables,
            variableReferences = VariableReferencesMap(mapOf())
        )
        typeCheck(node, typeContext)
        assertThat(variables[node.nodeId]!!, cast(equalTo(IntType)))
    }
}
