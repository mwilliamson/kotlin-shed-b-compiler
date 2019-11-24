package org.shedlang.compiler

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.*

fun isCast(call: CallBaseNode, references: ResolvedReferences): Boolean {
    val receiver = call.receiver
    return receiver is ReferenceNode && references[receiver] == Builtins.castBuiltin
}

object Builtins {
    val castBuiltin = builtinVariable("cast", CastType)
    val moduleName = builtinVariable("moduleName", StringType)
}

val builtins = listOf(
    builtinType("Any", AnyType),
    builtinType("Nothing", NothingType),
    builtinType("Unit", UnitType),
    builtinType("Int", IntType),
    builtinType("String", StringType),
    builtinType("StringIndex", StringIndexType),
    builtinType("CodePoint", CodePointType),
    builtinType("Bool", BoolType),
    builtinType("Symbol", AnySymbolType),
    builtinType("ShapeField", ShapeFieldTypeFunction),

    builtinEffect("Io", IoEffect),
    builtinEffect("Pure", EmptyEffect),

    Builtins.castBuiltin,
    Builtins.moduleName
)
