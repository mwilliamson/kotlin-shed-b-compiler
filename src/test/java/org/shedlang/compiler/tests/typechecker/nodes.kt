package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.tests.allOf
import org.shedlang.compiler.typechecker.*


fun emptyTypeContext(): TypeContext {
    return TypeContext(null, mutableMapOf())
}

fun typeContext(returnType: Type? = null, variables: MutableMap<String, Type> = mutableMapOf()): TypeContext {
    return TypeContext(returnType, variables)
}

fun anySourceLocation(): SourceLocation {
    return SourceLocation("<string>", 0)
}

fun ifStatement(
    condition: ExpressionNode = literalBool(true),
    trueBranch: List<StatementNode> = listOf(),
    falseBranch: List<StatementNode> = listOf()
): IfStatementNode {
    return IfStatementNode(condition, trueBranch, falseBranch, anySourceLocation())
}

// TODO: change to a statement that is bad in all situations once we have expression statements
private val badLocation = SourceLocation("<bad location>", 0)
private val badStatement = ReturnNode(literalInt(1), badLocation)
fun assertStatementIsTypeChecked(build: (StatementNode) -> StatementNode) {
    assertThat(
        { typeCheck(build(badStatement), emptyTypeContext()) },
        throws(has(ReturnOutsideOfFunctionError::location, equalTo(badLocation)))
    )
}

fun literalBool(value: Boolean) = BooleanLiteralNode(value, anySourceLocation())
fun literalInt(value: Int) = IntegerLiteralNode(value, anySourceLocation())
fun returns(expression: ExpressionNode) = ReturnNode(expression, anySourceLocation())

fun throwsUnexpectedType(expected: Type, actual: Type): Matcher<() -> Unit> {
    return throws(allOf(
        has(UnexpectedTypeError::expected, cast(equalTo(expected))),
        has(UnexpectedTypeError::actual, cast(equalTo(actual)))
    ))
}
