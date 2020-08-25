package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Source
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.NoSuchFieldError
import org.shedlang.compiler.typechecker.UnexpectedTypeError
import org.shedlang.compiler.typechecker.typeCheckFunctionStatement
import org.shedlang.compiler.types.BoolType
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.Type
import org.shedlang.compiler.types.UnitType

class TypeCheckValTests {
    @Test
    fun expressionIsTypeChecked() {
        val functionReference = variableReference("f")
        val node = valStatement(name = "x", expression = call(functionReference))
        assertThat(
            { typeCheckFunctionStatement(node, typeContext(referenceTypes = mapOf(functionReference to UnitType))) },
            throws(has(UnexpectedTypeError::actual, equalTo<Type>(UnitType)))
        )
    }

    @Test
    fun targetVariableTakesTypeOfExpression() {
        val target = targetVariable(name = "x")
        val node = valStatement(target = target, expression = literalInt())
        val typeContext = typeContext()

        typeCheckFunctionStatement(node, typeContext)

        assertThat(typeContext.typeOf(target), isIntType)
        assertThat(typeContext.typeOfTarget(target), isIntType)
    }
}
