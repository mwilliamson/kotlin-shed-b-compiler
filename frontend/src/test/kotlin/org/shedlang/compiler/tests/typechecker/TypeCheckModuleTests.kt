package org.shedlang.compiler.tests.typechecker

import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.StatementNode
import org.shedlang.compiler.typechecker.MetaType
import org.shedlang.compiler.typechecker.UnitType
import org.shedlang.compiler.typechecker.typeCheck

class TypeCheckModuleTests {
    @Test
    fun bodyIsTypeChecked() {
        assertStatementIsTypeChecked(fun(badStatement: StatementNode) {
            val unit = typeReference("Unit")
            val node = module(body = listOf(
                function(returnType = unit, body = listOf(badStatement))
            ))

            val typeContext = typeContext(referenceTypes = mapOf(unit to MetaType(UnitType)))
            typeCheck(node, typeContext)
        })
    }

    @Test
    fun functionsCanCallEachOtherRecursively() {
        val unit = typeReference("Unit")
        val referenceG = variableReference("g")
        val declarationF = function(name = "f", returnType = unit, body = listOf(
            expressionStatement(functionCall(referenceG, listOf()))
        ))
        val referenceF = variableReference("f")
        val declarationG = function(name = "g", returnType = unit, body = listOf(
            expressionStatement(functionCall(referenceF, listOf()))
        ))
        val node = module(body = listOf(
            declarationF,
            declarationG
        ))

        typeCheck(node, typeContext(
            referenceTypes = mapOf(unit to MetaType(UnitType)),
            references = mapOf(
                referenceF to declarationF,
                referenceG to declarationG
            )
        ))
    }
}
