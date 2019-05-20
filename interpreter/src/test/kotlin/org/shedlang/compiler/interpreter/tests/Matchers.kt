package org.shedlang.compiler.interpreter.tests

import com.natpryce.hamkrest.*
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.interpreter.*
import org.shedlang.compiler.tests.isPair
import org.shedlang.compiler.tests.isSequence


internal inline fun <T: Any, reified U: T> isPureResult(matcher: Matcher<U>): Matcher<EvaluationResult<T>> {
    return isResult(value = cast(matcher))
}

internal fun <T: Any> isResult(
    value: Matcher<T>,
    localFrameUpdates: Matcher<List<Pair<FrameReference.Local, List<Pair<Identifier, InterpreterValue>>>>> = isSequence()
): Matcher<EvaluationResult<T>> {
    return allOf(
        has(EvaluationResult<T>::value, value),
        has(EvaluationResult<T>::stdout, equalTo("")),
        has(EvaluationResult<T>::moduleValueUpdates, isSequence()),
        has(EvaluationResult<T>::moduleExpressionUpdates, isSequence()),
        has(EvaluationResult<T>::localFrameUpdates, localFrameUpdates)
    )
}

internal fun isBlock(
    body: Matcher<List<Statement>>,
    scope: Matcher<Scope> = anything
): Matcher<Expression> {
    return cast(allOf(
        has(Block::body, body),
        has(Block::scope, scope)
    ))
}

internal fun isVal(expression: Matcher<Expression>): Matcher<Statement> = cast(has(Val::expression, expression))

internal fun isBooleanValue(value: Boolean): Matcher<Expression> = cast(equalTo(BooleanValue(value)))
internal fun isIntegerValue(value: Int): Matcher<Expression> = cast(equalTo(IntegerValue(value)))
internal fun isStringValue(value: String): Matcher<Expression> = cast(equalTo(StringValue(value)))

internal fun isLocalFrameReference(frameId: LocalFrameId): Matcher<FrameReference> = cast(has(FrameReference.Local::id, equalTo(frameId)))

internal fun isLocalFrameUpdate(functionFrameId: LocalFrameId, variables: Matcher<Iterable<Pair<Identifier, InterpreterValue>>>): Matcher<Pair<FrameReference.Local, List<Pair<Identifier, InterpreterValue>>>> {
    return isPair(
        equalTo(FrameReference.Local(functionFrameId)),
        variables
    )
}
