package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Source
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.NoSuchFieldError
import org.shedlang.compiler.typechecker.typeCheckTarget
import org.shedlang.compiler.types.BoolType
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.TupleType

class TypeCheckTargetTests {
    @Test
    fun targetCanBeIgnored() {
        val target = targetIgnore()
        val typeContext = typeContext()

        typeCheckTarget(target, IntType, typeContext)
    }

    @Test
    fun targetVariableTakesTypeOfExpression() {
        val target = targetVariable(name = "x")
        val typeContext = typeContext()

        typeCheckTarget(target, IntType, typeContext)

        assertThat(typeContext.typeOf(target), isIntType)
        assertThat(typeContext.typeOfTarget(target), isIntType)
    }

    @Test
    fun targetTupleTakesTypeOfExpression() {
        val elementTarget1 = targetVariable("x")
        val elementTarget2 = targetVariable("y")
        val target = targetTuple(elements = listOf(
            elementTarget1,
            elementTarget2
        ))
        val typeContext = typeContext()

        typeCheckTarget(target, TupleType(listOf(IntType, BoolType)), typeContext)

        assertThat(typeContext.typeOfTarget(target), isTupleType(isSequence(isIntType, isBoolType)))
        assertThat(typeContext.typeOf(elementTarget1), isIntType)
        assertThat(typeContext.typeOfTarget(elementTarget1), isIntType)
        assertThat(typeContext.typeOf(elementTarget2), isBoolType)
        assertThat(typeContext.typeOfTarget(elementTarget2), isBoolType)

    }

    @Test
    fun whenTupleHasMoreElementsThanTargetThenErrorIsThrown() {
        val elementTarget1 = targetVariable("x")
        val target = targetTuple(elements = listOf(elementTarget1))
        val typeContext = typeContext()

        assertThat(
            { typeCheckTarget(target, TupleType(listOf(IntType, BoolType)), typeContext) },
            throwsUnexpectedType(
                actual = isTupleType(elementTypes = isSequence(isAnyType)),
                expected = cast(isTupleType(elementTypes = isSequence(isIntType, isBoolType))),
                source = equalTo(target.source)
            )
        )
    }

    @Test
    fun whenTupleHasFewerElementsThanTargetThenErrorIsThrown() {
        val elementTarget1 = targetVariable("x")
        val elementTarget2 = targetVariable("y")
        val target = targetTuple(elements = listOf(
            elementTarget1,
            elementTarget2
        ))
        val typeContext = typeContext()

        assertThat(
            { typeCheckTarget(target, TupleType(listOf(IntType)), typeContext) },
            throwsUnexpectedType(
                actual = isTupleType(elementTypes = isSequence(isAnyType, isAnyType)),
                expected = cast(isTupleType(elementTypes = isSequence(isIntType))),
                source = equalTo(target.source)
            )
        )
    }

    @Test
    fun fieldTargetsTakeTypeOfField() {
        val elementTarget1 = targetVariable("targetX")
        val elementTarget2 = targetVariable("targetY")
        val target = targetFields(fields = listOf(
            fieldName("x") to elementTarget1,
            fieldName("y") to elementTarget2
        ))
        val shapeType = shapeType(
            fields = listOf(
                field("x", IntType),
                field("y", BoolType)
            )
        )
        val typeContext = typeContext()

        typeCheckTarget(target, shapeType, typeContext)

        assertThat(typeContext.typeOfTarget(target), isType(shapeType))
        assertThat(typeContext.typeOf(elementTarget1), isIntType)
        assertThat(typeContext.typeOfTarget(elementTarget1), isIntType)
        assertThat(typeContext.typeOf(elementTarget2), isBoolType)
        assertThat(typeContext.typeOfTarget(elementTarget2), isBoolType)
    }

    @Test
    fun whenFieldIsMissingFromExpressionTypeThenErrorIsThrown() {
        val source = object: Source {
            override fun describe(): String {
                return "<source>"
            }
        }
        val elementTarget1 = targetVariable("targetX")
        val target = targetFields(fields = listOf(
            fieldName("x", source = source) to elementTarget1
        ))
        val shapeType = shapeType(
            fields = listOf()
        )
        val typeContext = typeContext()
        assertThat(
            { typeCheckTarget(target, shapeType, typeContext) },
            throws(allOf(
                has(NoSuchFieldError::fieldName, isIdentifier("x")),
                has(NoSuchFieldError::source, cast(equalTo(source)))
            ))
        )
    }
}
