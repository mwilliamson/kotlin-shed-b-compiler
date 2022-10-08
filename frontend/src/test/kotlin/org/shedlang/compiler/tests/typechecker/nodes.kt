package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.shedlang.compiler.InternalCompilerError
import org.shedlang.compiler.frontend.ModuleResult
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.typechecker.*
import org.shedlang.compiler.types.*
import java.util.*


internal fun emptyTypeContext(): TypeContext {
    return typeContext()
}

internal fun typeContext(
    moduleName: List<String> = listOf("Example"),
    effect: Effect = EmptyEffect,
    handle: HandleTypes? = null,
    expressionTypes: MutableMap<Int, Type> = mutableMapOf(),
    referenceTypes: Map<ReferenceNode, Type> = mapOf(),
    references: Map<ReferenceNode, VariableBindingNode> = mapOf(),
    typeRegistry: TypeRegistry = TypeRegistry.Empty,
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
        qualifiedPrefix = listOf(QualifiedNamePart.Module(moduleName.map(::Identifier))),
        effect = effect,
        handle = handle,
        expressionTypes = expressionTypes,
        targetTypes = mutableMapOf(),
        variableTypes = HashMap(finalTypes),
        refinedVariableTypes = mutableMapOf(),
        functionTypes = mutableMapOf(),
        discriminators = mutableMapOf(),
        resolvedReferences = ResolvedReferencesMap(finalReferences),
        typeRegistry = typeRegistry,
        deferred = LinkedList(),
        getModule = { moduleName -> modules[moduleName]!! }
    )
}

private val badSource = StringSource("<bad source>", "", 0)
private val badStatement = BadStatementNode(source = badSource)
fun assertStatementInStatementIsTypeChecked(build: (FunctionStatementNode) -> FunctionStatementNode) {
    assertStatementIsTypeChecked({ badStatement -> typeCheckFunctionStatement(build(badStatement), emptyTypeContext()) })
}
fun assertStatementIsTypeChecked(typeCheck: (FunctionStatementNode) -> Unit) {
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
    actual: Matcher<Type> = anything,
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
        has(InternalCompilerError::message, equalTo(message))
    )
}
