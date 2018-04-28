package org.shedlang.compiler

import org.shedlang.compiler.ast.BuiltinVariable
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
    builtinType("Unit", UnitType),
    builtinType("Int", IntType),
    builtinType("String", StringType),
    builtinType("Bool", BoolType),
    builtinType("List", ListType),

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
        nodeTypes = coreBuiltins.associate({ builtin -> builtin.nodeId to builtin.type}),
        resolvedReferences = resolvedReferences,
        getModule = { importPath -> throw UnsupportedOperationException() }
    )

    return evalType(node, typeContext)
}

internal val builtins = coreBuiltins + listOf(
    builtinVariable("print", parseType("(String) !Io -> Unit")),
    builtinVariable("intToString", parseType("(Int) -> String")),
    builtinVariable("list", ListConstructorType),

    builtinVariable("all", parseType("(List[Bool]) -> Bool")),
    builtinVariable("any", parseType("(List[Bool]) -> Bool")),
    builtinVariable("forEach", parseType("[T, !E]((T) !E -> Unit, List[T]) !E -> Unit")),
    builtinVariable("map", parseType("[T, R, !E]((T) !E -> R, List[T]) !E -> List[R]")),
    builtinVariable("reduce", parseType("[T, R, !E]((R, T) !E -> R, R, List[T]) !E -> R"))
)
