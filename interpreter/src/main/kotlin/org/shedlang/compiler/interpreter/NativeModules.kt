package org.shedlang.compiler.interpreter

import org.shedlang.compiler.ast.Identifier

private val listsModule = ModuleExpression(
    fieldExpressions = listOf(),
    fieldValues = listOf()
)

private val stringsModule = ModuleExpression(
    fieldExpressions = listOf(),
    fieldValues = listOf()
)

private val typesModule = ModuleExpression(
    fieldExpressions = listOf(),
    fieldValues = listOf()
)

internal val nativeModules: Map<List<Identifier>, ModuleExpression> = mapOf(
    listOf(Identifier("stdlib"), Identifier("platform"), Identifier("Lists")) to listsModule,
    listOf(Identifier("stdlib"), Identifier("platform"), Identifier("Strings")) to stringsModule,
    listOf(Identifier("stdlib"), Identifier("platform"), Identifier("Types")) to typesModule
)
