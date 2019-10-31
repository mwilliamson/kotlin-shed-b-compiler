package org.shedlang.compiler.stackinterpreter.tests

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.shedlang.compiler.stackinterpreter.*
import org.shedlang.compiler.types.Symbol


internal fun isBool(value: Boolean): Matcher<InterpreterValue> {
    return cast(has(InterpreterBool::value, equalTo(value)))
}

internal fun isCodePoint(value: Char): Matcher<InterpreterValue> {
    return isCodePoint(value.toInt())
}

internal fun isCodePoint(value: Int): Matcher<InterpreterValue> {
    return cast(has(InterpreterCodePoint::value, equalTo(value)))
}

internal fun isInt(value: Int): Matcher<InterpreterValue> {
    return cast(has(InterpreterInt::value, equalTo(value.toBigInteger())))
}

internal fun isString(value: String): Matcher<InterpreterValue> {
    return cast(has(InterpreterString::value, equalTo(value)))
}

internal fun isSymbol(value: Symbol): Matcher<InterpreterValue> {
    return cast(has(InterpreterSymbol::value, equalTo(value)))
}

internal fun isTuple(elements: Matcher<List<InterpreterValue>>): Matcher<InterpreterValue> {
    return cast(has(InterpreterTuple::elements, elements))
}

internal val isUnit = cast(equalTo(InterpreterUnit))
