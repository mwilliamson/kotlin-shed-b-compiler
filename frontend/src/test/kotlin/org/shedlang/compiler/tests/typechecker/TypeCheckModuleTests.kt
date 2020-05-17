package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.FunctionStatementNode
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.StaticValueType
import org.shedlang.compiler.types.UnitType

class TypeCheckModuleTests {
    @Test
    fun bodyIsTypeChecked() {
        assertStatementIsTypeChecked(fun(badStatement: FunctionStatementNode) {
            val unit = staticReference("Unit")
            val node = module(body = listOf(
                function(returnType = unit, body = listOf(badStatement))
            ))

            val typeContext = typeContext(referenceTypes = mapOf(unit to StaticValueType(UnitType)))
            typeCheck(node, typeContext)
        })
    }

    @Test
    fun functionsCanCallEachOtherRecursively() {
        val unit = staticReference("Unit")
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
            referenceTypes = mapOf(unit to StaticValueType(UnitType)),
            references = mapOf(
                referenceF to declarationF,
                referenceG to declarationG
            )
        ))
    }

    @Test
    fun functionsCanUseShapesBeforeSyntacticDeclaration() {
        val unitReference = staticReference("Unit")

        val shapeReference = staticReference("X")
        val shape = shape(name = "X")
        val node = module(body = listOf(
            function(
                parameters = listOf(parameter(type = shapeReference)),
                returnType = unitReference
            ),
            shape
        ))

        typeCheck(node, typeContext(
            references = mapOf(
                shapeReference to shape
            ),
            referenceTypes = mapOf(unitReference to StaticValueType(UnitType))
        ))
    }

    @Test
    fun shapeCanReferencePreviouslyDeclaredShape() {
        val firstShapeReference = staticReference("X")
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
        val unitReference = staticReference("Unit")
        val export = export("f")
        val exportedFunction = function(
            name = "f",
            returnType = unitReference
        )
        val node = module(
            exports = listOf(export),
            body = listOf(
                exportedFunction,

                function(
                    name = "g",
                    returnType = unitReference
                )
            )
        )

        val result = typeCheck(node, typeContext(
            references = mapOf(export to exportedFunction),
            referenceTypes = mapOf(unitReference to StaticValueType(UnitType))
        ))
        assertThat(result.fields, isMap(
            Identifier("f") to isFunctionType(returnType = equalTo(UnitType))
        ))
    }
}
