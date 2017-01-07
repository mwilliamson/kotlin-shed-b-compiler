package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.tests.allOf
import org.shedlang.compiler.typechecker.*
import java.util.*


fun emptyTypeContext(): TypeContext {
    return TypeContext(null, mutableMapOf())
}

fun typeContext(returnType: Type? = null, variables: Map<String, Type> = mapOf()): TypeContext {
    return TypeContext(returnType, HashMap(variables))
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

private val badLocation = SourceLocation("<bad location>", 0)
private val badStatement = ExpressionStatementNode(VariableReferenceNode("bad_variable_reference", badLocation), badLocation)
fun assertStatementInStatementIsTypeChecked(build: (StatementNode) -> StatementNode) {
    assertStatementIsTypeChecked({ badStatement -> typeCheck(build(badStatement), emptyTypeContext()) })
}
fun assertStatementIsTypeChecked(typeCheck: (StatementNode) -> Unit) {
    assertThat(
        { typeCheck(badStatement) },
        throws(has(UnresolvedReferenceError::location, equalTo(badLocation)))
    )
}

fun literalBool(value: Boolean) = BooleanLiteralNode(value, anySourceLocation())
fun literalInt(value: Int) = IntegerLiteralNode(value, anySourceLocation())
fun variableReference(name: String) = VariableReferenceNode(name, anySourceLocation())
fun returns(expression: ExpressionNode) = ReturnNode(expression, anySourceLocation())

fun functionCall(
    function: ExpressionNode,
    arguments: List<ExpressionNode> = listOf()
) = FunctionCallNode(
    function = function,
    arguments = arguments,
    location = anySourceLocation()
)

fun function(
    arguments: List<ArgumentNode> = listOf(),
    returnType: TypeNode = typeReference("Unit"),
    body: List<StatementNode> = listOf()
) = FunctionNode(
    name = "f",
    arguments = arguments,
    returnType = returnType,
    body = body,
    location = anySourceLocation()
)

fun argument(
    name: String,
    type: TypeNode
) = ArgumentNode(
    name = name,
    type = type,
    location = anySourceLocation()
)

fun typeReference(name: String) = TypeReferenceNode(name, anySourceLocation())

fun throwsUnexpectedType(expected: Type, actual: Type): Matcher<() -> Unit> {
    return throwsUnexpectedType(equalTo(expected), actual)
}

fun throwsUnexpectedType(expected: Matcher<Type>, actual: Type): Matcher<() -> Unit> {
    return throws(allOf(
        has(UnexpectedTypeError::expected, expected),
        has(UnexpectedTypeError::actual, cast(equalTo(actual)))
    ))
}
