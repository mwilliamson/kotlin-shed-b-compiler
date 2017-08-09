package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.tests.allOf
import org.shedlang.compiler.typechecker.*
import org.shedlang.compiler.types.Effect
import org.shedlang.compiler.types.ModuleType
import org.shedlang.compiler.types.Type
import java.util.*


fun emptyTypeContext(): TypeContext {
    return typeContext()
}

fun typeContext(
    returnType: Type? = null,
    effects: Set<Effect> = setOf(),
    referenceTypes: Map<ReferenceNode, Type> = mapOf(),
    references: Map<ReferenceNode, VariableBindingNode> = mapOf(),
    types: Map<VariableBindingNode, Type> = mapOf(),
    modules: Map<ImportPath, ModuleType> = mapOf()
): TypeContext {
    val finalReferences = (
        referenceTypes.keys.associateBy(ReferenceNode::nodeId, { entry -> freshNodeId()}) +
        references.entries.associateBy({ entry -> entry.key.nodeId }, { entry -> entry.value.nodeId })
    )
    val finalTypes = (
        referenceTypes.entries.associateBy({ entry -> finalReferences[entry.key.nodeId]!! }, { entry -> entry.value }) +
        types.entries.associateBy({ entry -> entry.key.nodeId }, { entry -> entry.value })
    )

    return TypeContext(
        returnType = returnType,
        effects = effects,
        nodeTypes = HashMap(finalTypes),
        resolvedReferences = ResolvedReferencesMap(finalReferences),
        deferred = mutableListOf(),
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



fun throwsUnexpectedType(expected: Type, actual: Type): Matcher<() -> Unit> {
    return throwsUnexpectedType(equalTo(expected), actual)
}

fun throwsUnexpectedType(expected: Matcher<Type>, actual: Type): Matcher<() -> Unit> {
    return throws(allOf(
        has(UnexpectedTypeError::expected, expected),
        has(UnexpectedTypeError::actual, cast(equalTo(actual)))
    ))
}

fun throwsCompilerError(message: String): Matcher<() -> Unit> {
    return throws(
        has(CompilerError::message, equalTo(message))
    )
}
