package org.shedlang.compiler.tests.typechecker

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.typechecker.Type
import org.shedlang.compiler.typechecker.TypeContext


fun emptyTypeContext(): TypeContext {
    return TypeContext(null, mutableMapOf())
}

fun typeContext(returnType: Type? = null, variables: MutableMap<String, Type> = mutableMapOf()): TypeContext {
    return TypeContext(returnType, variables)
}

fun anySourceLocation(): SourceLocation {
    return SourceLocation("<string>", 0)
}

fun literalBool(value: Boolean) = BooleanLiteralNode(value, anySourceLocation())
fun literalInt(value: Int) = IntegerLiteralNode(value, anySourceLocation())
fun returns(expression: ExpressionNode) = ReturnNode(expression, anySourceLocation())
