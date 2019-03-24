package org.shedlang.compiler.interpreter.tests

import com.natpryce.hamkrest.*
import org.shedlang.compiler.interpreter.Block
import org.shedlang.compiler.interpreter.EvaluationResult
import org.shedlang.compiler.interpreter.Scope
import org.shedlang.compiler.interpreter.Statement
import org.shedlang.compiler.tests.isSequence


internal inline fun <T: Any, reified U: T> isPureResult(matcher: Matcher<U>): Matcher<EvaluationResult<T>> {
    return allOf(
        has(EvaluationResult<T>::value, cast(matcher)),
        has(EvaluationResult<T>::stdout, equalTo("")),
        has(EvaluationResult<T>::moduleValueUpdates, isSequence()),
        has(EvaluationResult<T>::moduleExpressionUpdates, isSequence())
    )
}

internal fun isBlock(
    body: Matcher<List<Statement>>,
    scope: Matcher<Scope> = anything
): Matcher<Block> {
    return allOf(
        has(Block::body, body),
        has(Block::scope, scope)
    )
}
