package org.shedlang.compiler.backends.tests

import com.natpryce.hamkrest.*
import org.shedlang.compiler.stackir.*

fun isBool(expected: Boolean): Matcher<IrValue> {
    return cast(has(IrBool::value, equalTo(expected)))
}

fun isUnicodeScalar(expected: Int): Matcher<IrValue> {
    return cast(has(IrUnicodeScalar::value, equalTo(expected)))
}

fun isInt(expected: Int): Matcher<IrValue> {
    return cast(has(IrInt::value, equalTo(expected.toBigInteger())))
}

fun isString(expected: String): Matcher<IrValue> {
    return cast(has(IrString::value, equalTo(expected)))
}

val isUnit = isA<IrUnit>()
