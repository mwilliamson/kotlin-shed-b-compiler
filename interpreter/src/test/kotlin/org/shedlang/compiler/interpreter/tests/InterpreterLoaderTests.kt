package org.shedlang.compiler.interpreter.tests

import com.natpryce.hamkrest.allOf
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.BinaryOperator
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.backends.SimpleCodeInspector
import org.shedlang.compiler.backends.FieldValue
import org.shedlang.compiler.interpreter.*
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.types.Symbol


class LoadShapeTests {
    @Test
    fun whenConstantFieldHasExplicitValueThenValueIsLoaded() {
        val statement = shape(
            fields = listOf(
                shapeField(name = "x", value = literalInt(1)),
                shapeField(name = "y", value = literalInt(2))
            )
        )
        val context = LoaderContext(
            moduleName = listOf(),
            inspector = SimpleCodeInspector(
                shapeFields = mapOf(
                    statement to listOf(
                        fieldInspector(name = "x", value = FieldValue.Expression(literalInt(1))),
                        fieldInspector(name = "y", value = FieldValue.Expression(literalInt(2)))
                    )
                )
            )
        )

        val loadedStatements = loadModuleStatement(statement, context)

        assertThat(loadedStatements, isSequence(
            isVal(expression = cast(
                has(ShapeTypeValue::constantFields, isMap(
                    Identifier("x") to cast(equalTo(IntegerValue(1))),
                    Identifier("y") to cast(equalTo(IntegerValue(2)))
                ))
            ))
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
        val context = LoaderContext(
            moduleName = listOf(),
            inspector = SimpleCodeInspector(
                shapeFields = mapOf(
                    statement to listOf(
                        fieldInspector(name = "x", value = null)
                    )
                )
            )
        )

        val loadedStatements = loadModuleStatement(statement, context)

        assertThat(loadedStatements, isSequence(
            isVal(expression = cast(
                has(ShapeTypeValue::constantFields, isMap())
            ))
        ))
    }

    @Test
    fun whenConstantFieldHasNoExplicitValueThenValueIsInferredFromSymbolType() {
        val statement = shape(fields = listOf())
        val symbol = Symbol(module = listOf(Identifier("A"), Identifier("B")), name = "`C")
        val context = LoaderContext(
            moduleName = listOf(),
            inspector = SimpleCodeInspector(
                shapeFields = mapOf(
                    statement to listOf(
                        fieldInspector(name = "x", value = FieldValue.Symbol(symbol))
                    )
                )
            )
        )

        val loadedStatements = loadModuleStatement(statement, context)

        assertThat(loadedStatements, isSequence(
            isVal(expression = cast(has(ShapeTypeValue::constantFields, isMap(
                Identifier("x") to cast(equalTo(symbolValue(
                    module = listOf("A", "B"),
                    name = "`C"
                )))
            ))))
        ))
    }
}

class LoadIsTests {
    @Test
    fun isExpressionIsConvertedToBinaryOperationOnDiscriminatorField() {
        val variableReference = variableReference("x")
        val shapeReference = staticReference("Shape1")
        val node = isOperation(variableReference, shapeReference)
        val context = LoaderContext(
            moduleName = listOf(),
            inspector = SimpleCodeInspector(
                discriminatorsForIsExpressions = mapOf(
                    node to discriminator(symbolType(listOf("M"), "`A"), "tag")
                )
            )
        )

        val expression = loadExpression(node, context = context)

        assertThat(expression, cast(allOf(
            has(BinaryOperation::operator, equalTo(BinaryOperator.EQUALS)),
            has(BinaryOperation::left, cast(equalTo(FieldAccess(VariableReference("x"), Identifier("tag"))))),
            has(BinaryOperation::right, cast(equalTo(symbolValue(listOf("M"), "`A"))))
        )))
    }
}

class LoadTupleTests {
    @Test
    fun tupleLiteralIsLoadedAsCallToTupleConstructor() {
        val node = tupleNode(elements = listOf(literalInt(42)))

        val context = createContext()
        val expression = loadExpression(node, context = context)

        assertThat(expression, cast(allOf(
            has(Call::receiver, cast(equalTo(TupleConstructorValue))),
            has(Call::positionalArgumentExpressions, isSequence(
                isIntegerValue(42)
            )),
            has(Call::positionalArgumentValues, isSequence()),
            has(Call::namedArgumentExpressions, isSequence()),
            has(Call::namedArgumentValues, isSequence())
        )))
    }
}

private fun createContext(): LoaderContext {
    return LoaderContext(
        moduleName = listOf(),
        inspector = SimpleCodeInspector()
    )
}
