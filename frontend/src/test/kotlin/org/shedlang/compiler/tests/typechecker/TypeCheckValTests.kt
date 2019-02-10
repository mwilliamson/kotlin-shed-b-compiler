package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.FunctionStatementNode
import org.shedlang.compiler.tests.call
import org.shedlang.compiler.tests.literalInt
import org.shedlang.compiler.tests.valStatement
import org.shedlang.compiler.tests.variableReference
import org.shedlang.compiler.typechecker.ResolvedReferencesMap
import org.shedlang.compiler.typechecker.UnexpectedTypeError
import org.shedlang.compiler.typechecker.newTypeContext
import org.shedlang.compiler.typechecker.typeCheckFunctionStatement
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.Type
import org.shedlang.compiler.types.UnitType

class TypeCheckValTests {
    @Test
    fun expressionIsTypeChecked() {
        val functionReference = variableReference("f")
        val node = valStatement(name = "x", expression = call(functionReference))
        assertThat(
            { typeCheckFunctionStatement(node as FunctionStatementNode, typeContext(referenceTypes = mapOf(functionReference to UnitType))) },
            throws(has(UnexpectedTypeError::actual, equalTo<Type>(UnitType)))
        )
    }

    @Test
    fun valTakesTypeOfExpression() {
        val node = valStatement(name = "x", expression = literalInt())
        val typeContext = newTypeContext(
            moduleName = null,
            nodeTypes = mapOf(),
            resolvedReferences = ResolvedReferencesMap(mapOf()),
            getModule = { moduleName -> throw UnsupportedOperationException() }
        )
        typeCheckFunctionStatement(node as FunctionStatementNode, typeContext)
        assertThat(typeContext.typeOf(node), cast(equalTo(IntType)))
    }
}
