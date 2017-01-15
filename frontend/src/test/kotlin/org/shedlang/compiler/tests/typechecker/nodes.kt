package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.tests.allOf
import org.shedlang.compiler.typechecker.*
import java.util.*


fun emptyTypeContext(): TypeContext {
    return typeContext()
}

fun typeContext(
    returnType: Type? = null,
    referenceTypes: Map<ReferenceNode, Type> = mapOf(),
    references: Map<ReferenceNode, VariableBindingNode> = mapOf()
): TypeContext {
    val finalReferences = (
        referenceTypes.keys.associateBy(ReferenceNode::nodeId, { entry -> nextId()}) +
        references.entries.associateBy({ entry -> entry.key.nodeId }, { entry -> entry.value.nodeId })
    )
    val types = referenceTypes.entries.associateBy({ entry -> finalReferences[entry.key.nodeId]!! }, { entry -> entry.value })

    return TypeContext(returnType, HashMap(types), VariableReferencesMap(finalReferences))
}

class VariableReferencesMap(private val references: Map<Int, Int>) : VariableReferences {
    override fun get(node: ReferenceNode): Int {
        return references[node.nodeId]!!
    }
}

fun anySource(): Source {
    return StringSource("<string>", 0)
}

fun ifStatement(
    condition: ExpressionNode = literalBool(true),
    trueBranch: List<StatementNode> = listOf(),
    falseBranch: List<StatementNode> = listOf()
): IfStatementNode {
    return IfStatementNode(condition, trueBranch, falseBranch, anySource())
}

fun expressionStatement(expression: ExpressionNode) = ExpressionStatementNode(expression, anySource())

private val badSource = StringSource("<bad source>", 0)
private val badStatement = BadStatementNode(source = badSource)
fun assertStatementInStatementIsTypeChecked(build: (StatementNode) -> StatementNode) {
    assertStatementIsTypeChecked({ badStatement -> typeCheck(build(badStatement), emptyTypeContext()) })
}
fun assertStatementIsTypeChecked(typeCheck: (StatementNode) -> Unit) {
    assertThat(
        { typeCheck(badStatement) },
        throws(has(BadStatementError::location, cast(equalTo(badSource))))
    )
}

fun literalBool(value: Boolean) = BooleanLiteralNode(value, anySource())
fun literalInt(value: Int = 0) = IntegerLiteralNode(value, anySource())
fun variableReference(name: String) = VariableReferenceNode(name, anySource())
fun returns(expression: ExpressionNode) = ReturnNode(expression, anySource())

fun binaryOperation(
    operator: Operator,
    left: ExpressionNode,
    right: ExpressionNode
) = BinaryOperationNode(
    operator = operator,
    left = left,
    right = right,
    source = anySource()
)

fun functionCall(
    function: ExpressionNode,
    arguments: List<ExpressionNode> = listOf()
) = FunctionCallNode(
    function = function,
    arguments = arguments,
    source = anySource()
)

fun function(
    name: String = "f",
    arguments: List<ArgumentNode> = listOf(),
    returnType: TypeNode = typeReference("Unit"),
    body: List<StatementNode> = listOf()
) = FunctionNode(
    name = name,
    arguments = arguments,
    returnType = returnType,
    body = body,
    source = anySource()
)

fun argument(
    name: String,
    type: TypeNode = typeReference("Int")
) = ArgumentNode(
    name = name,
    type = type,
    source = anySource()
)

fun module(
    body: List<FunctionNode>
) = ModuleNode(
    name = "",
    body = body,
    source = anySource()
)

fun typeReference(name: String) = TypeReferenceNode(name, anySource())

fun throwsUnexpectedType(expected: Type, actual: Type): Matcher<() -> Unit> {
    return throwsUnexpectedType(equalTo(expected), actual)
}

fun throwsUnexpectedType(expected: Matcher<Type>, actual: Type): Matcher<() -> Unit> {
    return throws(allOf(
        has(UnexpectedTypeError::expected, expected),
        has(UnexpectedTypeError::actual, cast(equalTo(actual)))
    ))
}
