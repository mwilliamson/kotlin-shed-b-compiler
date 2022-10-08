package org.shedlang.compiler

import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.*

object Builtins {
    val moduleName = builtinVariable("moduleName", StringType)
}

val builtins = listOf(
    builtinType("Any", AnyType),
    builtinType("Nothing", NothingType),
    builtinType("Unit", UnitType),
    builtinType("Int", IntType),
    builtinType("String", StringType),
    builtinType("StringSlice", StringSliceType),
    builtinType("UnicodeScalar", UnicodeScalarType),
    builtinType("Bool", BoolType),
    builtinVariable("ShapeField", TypeLevelValueType(ShapeFieldTypeFunction)),

    builtinVariable("Castable", TypeLevelValueType(CastableTypeLevelFunction)),
    builtinVariable("Type", TypeLevelValueType(MetaTypeTypeLevelFunction)),

    builtinEffect("Io", IoEffect),
    builtinEffect("Pure", EmptyEffect),

    Builtins.moduleName
)
