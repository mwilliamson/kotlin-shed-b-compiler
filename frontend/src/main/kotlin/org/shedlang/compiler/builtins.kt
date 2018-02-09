package org.shedlang.compiler

import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.parser.parse
import org.shedlang.compiler.typechecker.evalType
import org.shedlang.compiler.typechecker.newTypeContext
import org.shedlang.compiler.typechecker.resolve
import org.shedlang.compiler.types.*

internal data class Builtin(
    val name: String,
    val type: Type
) {
    val nodeId = freshNodeId()
}

private val coreBuiltins = listOf(
    Builtin("Any", MetaType(AnyType)),
    Builtin("Unit", MetaType(UnitType)),
    Builtin("Int", MetaType(IntType)),
    Builtin("String", MetaType(StringType)),
    Builtin("Bool", MetaType(BoolType)),
    Builtin("List", MetaType(ListType)),

    Builtin("!Io", EffectType(IoEffect))
)

fun parseType(string: String): Type {
    val node = parse(
        filename = "<string>",
        input = string,
        rule = { tokens -> org.shedlang.compiler.parser.parseStaticExpression(tokens) }
    )
    val resolvedReferences = resolve(
        node,
        coreBuiltins.associate({ builtin -> builtin.name to builtin.nodeId})
    )

    val typeContext = newTypeContext(
        nodeTypes = coreBuiltins.associate({ builtin -> builtin.nodeId to builtin.type}),
        resolvedReferences = resolvedReferences,
        getModule = { importPath -> throw UnsupportedOperationException() }
    )

    return evalType(node, typeContext)
}

internal val builtins = coreBuiltins + listOf(
    Builtin("print", parseType("(String) !Io -> Unit")),
    Builtin("intToString", parseType("(Int) -> String")),
    Builtin("list", ListConstructorType),

    Builtin("all", parseType("(List[Bool]) -> Bool")),
    Builtin("any", parseType("(List[Bool]) -> Bool")),
    Builtin("forEach", parseType("[T, !E]((T) !E -> Unit, List[T]) !E -> Unit")),
    Builtin("map", parseType("[T, R, !E]((T) !E -> R, List[T]) !E -> List[R]"))
)
