package org.shedlang.compiler

import org.shedlang.compiler.ast.BuiltinVariable
import org.shedlang.compiler.parser.parse
import org.shedlang.compiler.typechecker.evalType
import org.shedlang.compiler.typechecker.newTypeContext
import org.shedlang.compiler.typechecker.resolve
import org.shedlang.compiler.types.*

private val coreBuiltins = listOf(
    BuiltinVariable("Any", MetaType(AnyType)),
    BuiltinVariable("Unit", MetaType(UnitType)),
    BuiltinVariable("Int", MetaType(IntType)),
    BuiltinVariable("String", MetaType(StringType)),
    BuiltinVariable("Bool", MetaType(BoolType)),
    BuiltinVariable("List", MetaType(ListType)),

    BuiltinVariable("Io", EffectType(IoEffect)),
    BuiltinVariable("Pure", EffectType(EmptyEffect))
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
    BuiltinVariable("print", parseType("(String) !Io -> Unit")),
    BuiltinVariable("intToString", parseType("(Int) -> String")),
    BuiltinVariable("list", ListConstructorType),

    BuiltinVariable("all", parseType("(List[Bool]) -> Bool")),
    BuiltinVariable("any", parseType("(List[Bool]) -> Bool")),
    BuiltinVariable("forEach", parseType("[T, !E]((T) !E -> Unit, List[T]) !E -> Unit")),
    BuiltinVariable("map", parseType("[T, R, !E]((T) !E -> R, List[T]) !E -> List[R]")),
    BuiltinVariable("reduce", parseType("[T, R, !E]((R, T) !E -> R, R, List[T]) !E -> R"))
)
