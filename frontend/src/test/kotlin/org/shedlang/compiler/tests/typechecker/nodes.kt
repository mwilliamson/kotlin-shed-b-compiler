package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.shedlang.compiler.ModuleResult
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.tests.allOf
import org.shedlang.compiler.typechecker.*
import org.shedlang.compiler.types.Effect
import org.shedlang.compiler.types.EmptyEffect
import org.shedlang.compiler.types.Type
import org.shedlang.compiler.types.TypeGroup
import java.util.*


internal fun emptyTypeContext(): TypeContext {
    return typeContext()
}

internal fun typeContext(
    moduleName: List<String>? = null,
    effect: Effect = EmptyEffect,
    expressionTypes: MutableMap<Int, Type> = mutableMapOf(),
    referenceTypes: Map<ReferenceNode, Type> = mapOf(),
    references: Map<ReferenceNode, VariableBindingNode> = mapOf(),
    types: Map<VariableBindingNode, Type> = mapOf(),
    modules: Map<ImportPath, ModuleResult> = mapOf()
): TypeContext {
    val finalReferences = (
        referenceTypes.entries.associateBy(
            { entry -> entry.key.nodeId },
            { entry -> BuiltinVariable(entry.key.name, entry.value) }
        ) +
        references.entries.associateBy({ entry -> entry.key.nodeId }, { entry -> entry.value })
    )
    val finalTypes = (
        referenceTypes.entries.associateBy({ entry -> finalReferences[entry.key.nodeId]!!.nodeId }, { entry -> entry.value }) +
        types.entries.associateBy({ entry -> entry.key.nodeId }, { entry -> entry.value })
    )

    return TypeContext(
        moduleName = moduleName,
        effect = effect,
        expressionTypes = expressionTypes,
        variableTypes = HashMap(finalTypes),
        resolvedReferences = ResolvedReferencesMap(finalReferences),
        deferred = LinkedList(),
        getModule = { moduleName -> modules[moduleName]!! }
    )
}

private val badSource = StringSource("<bad source>", "", 0)
private val badStatement = BadStatementNode(source = badSource)
fun assertStatementInStatementIsTypeChecked(build: (StatementNode) -> StatementNode) {
    assertStatementIsTypeChecked({ badStatement -> typeCheck(build(badStatement), emptyTypeContext()) })
}
fun assertStatementIsTypeChecked(typeCheck: (StatementNode) -> Unit) {
    assertThat(
        { typeCheck(badStatement) },
        throws(has(BadStatementError::source, cast(equalTo(badSource))))
    )
}



fun throwsUnexpectedType(expected: TypeGroup, actual: Type): Matcher<() -> Unit> {
    return throwsUnexpectedType(equalTo(expected), actual)
}

fun throwsUnexpectedType(expected: Matcher<TypeGroup>, actual: Type): Matcher<() -> Unit> {
    return throws(allOf(
        has(UnexpectedTypeError::expected, expected),
        has(UnexpectedTypeError::actual, cast(equalTo(actual)))
    ))
}

fun throwsUnexpectedType(
    expected: Matcher<TypeGroup>,
    actual: Matcher<Type>,
    source: Matcher<Source> = anything
): Matcher<() -> Unit> {
    return throws(allOf(
        has(UnexpectedTypeError::expected, expected),
        has(UnexpectedTypeError::actual, actual),
        has(UnexpectedTypeError::source, source)
    ))
}

fun throwsCompilerError(message: String): Matcher<() -> Unit> {
    return throws(
        has(CompilerError::message, equalTo(message))
    )
}
