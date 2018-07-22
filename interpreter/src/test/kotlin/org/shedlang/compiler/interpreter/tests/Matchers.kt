package org.shedlang.compiler.interpreter.tests

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.has
import org.shedlang.compiler.interpreter.Block
import org.shedlang.compiler.interpreter.EvaluationResult
import org.shedlang.compiler.interpreter.Statement


internal inline fun <T: Any, reified U: T> isPureResult(matcher: Matcher<U>): Matcher<EvaluationResult<T>> {
    return cast(has(EvaluationResult.Value<T>::value, cast(matcher)))
}

internal fun isBlock(body: Matcher<List<Statement>>): Matcher<Block> {
    return has(Block::body, body)
}
