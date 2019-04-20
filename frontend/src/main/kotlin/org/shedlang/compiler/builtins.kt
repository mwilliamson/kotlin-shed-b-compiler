package org.shedlang.compiler

import org.shedlang.compiler.ast.builtinEffect
import org.shedlang.compiler.ast.builtinType
import org.shedlang.compiler.ast.builtinVariable
import org.shedlang.compiler.parser.parse
import org.shedlang.compiler.typechecker.evalType
import org.shedlang.compiler.typechecker.newTypeContext
import org.shedlang.compiler.typechecker.resolve
import org.shedlang.compiler.types.*

private val coreBuiltins = listOf(
    builtinType("Any", AnyType),
    builtinType("Nothing", NothingType),
    builtinType("Unit", UnitType),
    builtinType("Int", IntType),
    builtinType("String", StringType),
    builtinType("CodePoint", CodePointType),
    builtinType("Bool", BoolType),
    builtinType("List", ListType),
    builtinType("Type", metaType),
    builtinType("Symbol", AnySymbolType),

    builtinEffect("Io", IoEffect),
    builtinEffect("Pure", EmptyEffect)
)

fun parseType(string: String): Type {
    val node = parse(
        filename = "<string>",
        input = string,
        rule = { tokens -> org.shedlang.compiler.parser.parseStaticExpression(tokens) }
    )
    val resolvedReferences = resolve(
        node,
        coreBuiltins.associate({ builtin -> builtin.name to builtin})
    )

    val typeContext = newTypeContext(
        moduleName = null,
        nodeTypes = coreBuiltins.associate({ builtin -> builtin.nodeId to builtin.type}),
        resolvedReferences = resolvedReferences,
        getModule = { importPath -> throw UnsupportedOperationException() }
    )

    return evalType(node, typeContext)
}

object Builtins {
    val intToString = builtinVariable("intToString", parseType("Fun (Int) -> String"))
}

internal val builtins = coreBuiltins + listOf(
    builtinVariable("print", parseType("Fun (String) !Io -> Unit")),
    Builtins.intToString,
    builtinVariable("list", ListConstructorType),

    builtinVariable("moduleName", StringType)
)
