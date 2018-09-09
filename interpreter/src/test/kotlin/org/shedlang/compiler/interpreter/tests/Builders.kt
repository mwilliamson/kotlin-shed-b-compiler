package org.shedlang.compiler.interpreter.tests

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.interpreter.*
import org.shedlang.compiler.types.Symbol


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
    return Scope(listOf(ScopeFrameMap(variables)))
}

internal fun call(
    receiver: Expression,
    positionalArgumentExpressions: List<Expression> = listOf(),
    positionalArgumentValues: List<InterpreterValue> = listOf(),
    namedArgumentExpressions: List<Pair<Identifier, Expression>> = listOf(),
    namedArgumentValues: List<Pair<Identifier, InterpreterValue>> = listOf()
): Call {
    return Call(
        receiver,
        positionalArgumentExpressions = positionalArgumentExpressions,
        positionalArgumentValues = positionalArgumentValues,
        namedArgumentExpressions = namedArgumentExpressions,
        namedArgumentValues = namedArgumentValues
    )
}

internal fun returns(expression: IntegerValue) = ExpressionStatement(expression, isReturn = true)

internal fun symbolValue(module: List<String>, name: String): SymbolValue {
    return SymbolValue(Symbol(module.map(::Identifier), name))
}
