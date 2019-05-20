package org.shedlang.compiler.interpreter.tests

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.interpreter.*
import org.shedlang.compiler.types.Symbol
import java.util.*

internal fun createLocalContext(vararg variablesInFrames: Map<String, InterpreterValue>): InterpreterContext {
    val (frameReferences, localFrames) = variablesInFrames.map { variablesInFrame ->
        val frameId = createLocalFrameId()
        val frameReference = FrameReference.Local(frameId)
        frameReference to (frameId to ScopeFrameMap(variablesInFrame))
    }.unzip()

    val frameId = createLocalFrameId()
    val frameReference = FrameReference.Local(frameId)
    return InterpreterContext(
        scope = Scope(frameReferences = frameReferences),
        localFrames = WeakHashMap(localFrames.toMap()),
        moduleExpressions = mapOf(),
        moduleValues = mapOf()
    )
}

internal fun createContext(
    scope: Scope = Scope(listOf()),
    localFrames: WeakHashMap<LocalFrameId, ScopeFrameMap> = WeakHashMap(),
    moduleValues: Map<List<Identifier>, ModuleValue> = mapOf(),
    moduleExpressions: Map<List<Identifier>, ModuleExpression> = mapOf()
): InterpreterContext {
    return InterpreterContext(
        scope = scope,
        moduleValues = moduleValues,
        moduleExpressions = moduleExpressions,
        localFrames = localFrames
    )
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
