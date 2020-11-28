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
    builtinVariable("ShapeField", StaticValueType(ShapeFieldTypeFunction)),

    builtinVariable("Castable", StaticValueType(CastableTypeFunction)),
    builtinVariable("Type", StaticValueType(MetaTypeTypeFunction)),

    builtinVariable("Empty", StaticValueType(EmptyTypeFunction)),
    builtinVariable("empty", EmptyFunctionType),

    builtinEffect("Io", IoEffect),
    builtinEffect("Pure", EmptyEffect),

    Builtins.moduleName
)
