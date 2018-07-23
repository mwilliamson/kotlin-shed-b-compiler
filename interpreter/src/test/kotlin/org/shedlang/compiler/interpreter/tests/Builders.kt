package org.shedlang.compiler.interpreter.tests

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.interpreter.*


internal fun createContext(
    scope: Scope = Scope(listOf()),
    moduleValues: Map<List<Identifier>, ModuleValue> = mapOf(),
    moduleExpressions: Map<List<Identifier>, ModuleExpression> = mapOf()
): InterpreterContext {
    return InterpreterContext(
        scope = scope,
        moduleValues = moduleValues,
        moduleExpressions = moduleExpressions
    )
}

internal fun scopeOf(variables: Map<String, InterpreterValue>): Scope {
    return Scope(listOf(ScopeFrame(variables)))
}

internal fun call(
    receiver: Expression,
    positionalArgumentExpressions: List<Expression> = listOf(),
    positionalArgumentValues: List<InterpreterValue> = listOf(),
    namedArgumentValues: List<Pair<Identifier, InterpreterValue>> = listOf()
): Call {
    return Call(
        receiver,
        positionalArgumentExpressions = positionalArgumentExpressions,
        positionalArgumentValues = positionalArgumentValues,
        namedArgumentValues = namedArgumentValues
    )
}

internal fun returns(expression: IntegerValue) = ExpressionStatement(expression, isReturn = true)
