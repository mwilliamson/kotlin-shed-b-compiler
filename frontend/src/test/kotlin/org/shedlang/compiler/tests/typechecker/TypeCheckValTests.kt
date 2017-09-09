package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.StatementNode
import org.shedlang.compiler.testing.call
import org.shedlang.compiler.testing.literalInt
import org.shedlang.compiler.testing.valStatement
import org.shedlang.compiler.testing.variableReference
import org.shedlang.compiler.typechecker.ResolvedReferencesMap
import org.shedlang.compiler.typechecker.UnexpectedTypeError
import org.shedlang.compiler.typechecker.newTypeContext
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.Type
import org.shedlang.compiler.types.UnitType

class TypeCheckValTests {
    @Test
    fun expressionIsTypeChecked() {
        val functionReference = variableReference("f")
        val node = valStatement(name = "x", expression = call(functionReference))
        assertThat(
            { typeCheck(node as StatementNode, typeContext(referenceTypes = mapOf(functionReference to UnitType))) },
            throws(has(UnexpectedTypeError::actual, equalTo<Type>(UnitType)))
        )
    }

    @Test
    fun valTakesTypeOfExpression() {
        val node = valStatement(name = "x", expression = literalInt())
        val variables = mutableMapOf<Int, Type>()
        val typeContext = newTypeContext(
            nodeTypes = variables,
            resolvedReferences = ResolvedReferencesMap(mapOf()),
            getModule = { moduleName -> throw UnsupportedOperationException() }
        )
        typeCheck(node as StatementNode, typeContext)
        assertThat(variables[node.nodeId]!!, cast(equalTo(IntType)))
    }
}
