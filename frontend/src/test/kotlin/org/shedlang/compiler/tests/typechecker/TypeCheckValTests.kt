package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
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
        val target = valTargetVariable(name = "x")
        val node = valStatement(target = target, expression = literalInt())
        val typeContext = typeContext()

        typeCheckFunctionStatement(node, typeContext)

        assertThat(typeContext.typeOf(target), cast(equalTo(IntType)))
    }

    @Test
    fun targetTupleTakesTypeOfExpression() {
        val elementTarget1 = valTargetVariable("x")
        val elementTarget2 = valTargetVariable("y")
        val target = valTargetTuple(elements = listOf(
            elementTarget1,
            elementTarget2
        ))
        val expression = tupleNode(listOf(literalInt(), literalBool()))
        val node = valStatement(target = target, expression = expression)
        val typeContext = typeContext()

        typeCheckFunctionStatement(node, typeContext)

        assertThat(typeContext.typeOf(elementTarget1), isIntType)
        assertThat(typeContext.typeOf(elementTarget2), isBoolType)
    }

    @Test
    fun whenTupleHasMoreElementsThanTargetThenErrorIsThrown() {
        val elementTarget1 = valTargetVariable("x")
        val target = valTargetTuple(elements = listOf(elementTarget1))
        val expression = tupleNode(listOf(literalInt(), literalBool()))
        val node = valStatement(target = target, expression = expression)
        val typeContext = typeContext()

        assertThat(
            { typeCheckFunctionStatement(node, typeContext) },
            throwsUnexpectedType(
                actual = isTupleType(elementTypes = isSequence(isAnyType)),
                expected = cast(isTupleType(elementTypes = isSequence(isIntType, isBoolType))),
                source = equalTo(target.source)
            )
        )
    }

    @Test
    fun whenTupleHasFewerElementsThanTargetThenErrorIsThrown() {
        val elementTarget1 = valTargetVariable("x")
        val elementTarget2 = valTargetVariable("y")
        val target = valTargetTuple(elements = listOf(
            elementTarget1,
            elementTarget2
        ))
        val expression = tupleNode(listOf(literalInt()))
        val node = valStatement(target = target, expression = expression)
        val typeContext = typeContext()

        assertThat(
            { typeCheckFunctionStatement(node, typeContext) },
            throwsUnexpectedType(
                actual = isTupleType(elementTypes = isSequence(isAnyType, isAnyType)),
                expected = cast(isTupleType(elementTypes = isSequence(isIntType))),
                source = equalTo(target.source)
            )
        )
    }

    @Test
    fun fieldTargetsTakeTypeOfField() {
        val elementTarget1 = valTargetVariable("targetX")
        val elementTarget2 = valTargetVariable("targetY")
        val target = valTargetFields(fields = listOf(
            fieldName("x") to elementTarget1,
            fieldName("y") to elementTarget2
        ))
        val expressionDeclaration = declaration("e")
        val expression = variableReference("e")
        val node = valStatement(target = target, expression = expression)
        val typeContext = typeContext(
            references = mapOf(
                expression to expressionDeclaration
            ),
            types = mapOf(
                expressionDeclaration to shapeType(
                    fields = listOf(
                        field("x", IntType),
                        field("y", BoolType)
                    )
                )
            )
        )

        typeCheckFunctionStatement(node, typeContext)

        assertThat(typeContext.typeOf(elementTarget1), isIntType)
        assertThat(typeContext.typeOf(elementTarget2), isBoolType)
    }
}
