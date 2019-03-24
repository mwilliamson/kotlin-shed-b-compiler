package org.shedlang.compiler.interpreter.tests

import com.natpryce.hamkrest.allOf
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.TypesMap
import org.shedlang.compiler.ast.BinaryOperator
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.interpreter.*
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.MetaType
import org.shedlang.compiler.types.freshShapeId


class LoadShapeTests {
    @Test
    fun whenConstantFieldHasExplicitValueThenValueIsLoaded() {
        val statement = shape(
            fields = listOf(
                shapeField(
                    name = "x",
                    value = literalInt(1)
                ),
                shapeField(
                    name = "y",
                    value = literalInt(2)
                )
            )
        )
        val shapeType = shapeType(
            fields = listOf(
                field(
                    name = "x",
                    type = IntType,
                    isConstant = true
                ),
                field(
                    name = "y",
                    type = IntType,
                    isConstant = true
                )
            )
        )
        val context = LoaderContext(
            moduleName = listOf(),
            types = TypesMap(
                expressionTypes = mapOf(),
                variableTypes = mapOf(statement.nodeId to MetaType(shapeType))
            )
        )

        val loadedStatement = loadModuleStatement(statement, context).single()

        assertThat(loadedStatement, isPair(
            second = cast(
                has(ShapeTypeValue::constantFields, isMap(
                    Identifier("x") to cast(equalTo(IntegerValue(1))),
                    Identifier("y") to cast(equalTo(IntegerValue(2)))
                ))
            )
        ))
    }

    @Test
    fun nonConstantFieldsAreIgnoredWhenGeneratingConstantFields() {
        val statement = shape(
            fields = listOf(
                shapeField(
                    name = "x",
                    value = null
                )
            )
        )
        val shapeType = shapeType(
            fields = listOf(
                field(
                    name = "x",
                    type = IntType,
                    isConstant = false
                )
            )
        )
        val context = LoaderContext(
            moduleName = listOf(),
            types = TypesMap(
                expressionTypes = mapOf(),
                variableTypes = mapOf(statement.nodeId to MetaType(shapeType))
            )
        )

        val loadedStatement = loadModuleStatement(statement, context).single()

        assertThat(loadedStatement, isPair(
            second = cast(
                has(ShapeTypeValue::constantFields, isMap())
            )
        ))
    }

    @Test
    fun whenConstantFieldHasNoExplicitValueThenValueIsInferredFromSymbolType() {
        val statement = shape(
            fields = listOf()
        )
        val shapeType = shapeType(
            fields = listOf(
                field(
                    name = "x",
                    type = symbolType(module = listOf("A", "B"), name = "@C"),
                    isConstant = true
                )
            )
        )
        val context = LoaderContext(
            moduleName = listOf(),
            types = TypesMap(
                expressionTypes = mapOf(),
                variableTypes = mapOf(statement.nodeId to MetaType(shapeType))
            )
        )

        val loadedStatement = loadModuleStatement(statement, context).single()

        assertThat(loadedStatement, isPair(
            second = cast(
                has(ShapeTypeValue::constantFields, isMap(
                    Identifier("x") to cast(equalTo(symbolValue(
                        module = listOf("A", "B"),
                        name = "@C"
                    )))
                ))
            )
        ))
    }
}

class LoadIsTests {
    @Test
    fun isExpressionIsConvertedToBinaryOperationOnDiscriminatorField() {
        val shapeId = freshShapeId()
        val shapeType1 = shapeType(
            fields = listOf(
                field("tag", symbolType(listOf("M"), "@A"), shapeId = shapeId)
            )
        )
        val shapeType2 = shapeType(
            fields = listOf(
                field("tag", symbolType(listOf("M"), "@B"), shapeId = shapeId)
            )
        )
        val unionType = unionType(members = listOf(shapeType1, shapeType2))

        val variableReference = variableReference("x")
        val shapeReference = staticReference("Shape1")
        val node = isOperation(variableReference, shapeReference)
        val context = LoaderContext(
            moduleName = listOf(),
            types = TypesMap(
                expressionTypes = mapOf(
                    variableReference.nodeId to unionType,
                    shapeReference.nodeId to MetaType(shapeType1)
                ),
                variableTypes = mapOf()
            )
        )

        val expression = loadExpression(node, context = context)

        assertThat(expression, cast(allOf(
            has(BinaryOperation::operator, equalTo(BinaryOperator.EQUALS)),
            has(BinaryOperation::left, cast(equalTo(FieldAccess(VariableReference("x"), Identifier("tag"))))),
            has(BinaryOperation::right, cast(equalTo(symbolValue(listOf("M"), "@A"))))
        )))
    }
}
