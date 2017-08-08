package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.StatementNode
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.MetaType
import org.shedlang.compiler.types.UnitType

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
            expressionStatement(call(referenceG, listOf()))
        ))
        val referenceF = variableReference("f")
        val declarationG = function(name = "g", returnType = unit, body = listOf(
            expressionStatement(call(referenceF, listOf()))
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

    @Test
    fun functionsCanUseShapesBeforeSyntacticDeclaration() {
        val unitReference = typeReference("Unit")

        val shapeReference = typeReference("X")
        val shape = shape(name = "X")
        val node = module(body = listOf(
            function(
                arguments = listOf(argument(type = shapeReference)),
                returnType = unitReference
            ),
            shape
        ))

        typeCheck(node, typeContext(
            references = mapOf(
                shapeReference to shape
            ),
            referenceTypes = mapOf(unitReference to MetaType(UnitType))
        ))
    }

    @Test
    fun shapeCanReferencePreviouslyDeclaredShape() {
        val firstShapeReference = typeReference("X")
        val firstShape = shape(name = "X")
        val secondShape = shape(name = "Y", fields = listOf(shapeField("x", firstShapeReference)))
        val node = module(body = listOf(
            firstShape,
            secondShape
        ))

        typeCheck(node, typeContext(
            references = mapOf(
                firstShapeReference to firstShape
            )
        ))
    }

    @Test
    fun typeOfModuleIsReturned() {
        val unitReference = typeReference("Unit")
        val node = module(
            body = listOf(
                function(
                    name = "f",
                    returnType = unitReference
                )
            )
        )

        val result = typeCheck(node, typeContext(
            referenceTypes = mapOf(unitReference to MetaType(UnitType))
        ))
        assertThat(result.fields, isMap(
            "f" to isFunctionType(returnType = equalTo(UnitType))
        ))
    }
}
