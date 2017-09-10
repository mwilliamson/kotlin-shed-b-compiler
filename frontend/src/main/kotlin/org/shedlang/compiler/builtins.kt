import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.types.*

internal data class Builtin(
    val name: String,
    val type: Type
) {
    val nodeId = freshNodeId()
}

private val mapFromType = TypeParameter("T", variance = Variance.INVARIANT)
private val mapToType = TypeParameter("R", variance = Variance.INVARIANT)
private val mapEffectParameter = EffectParameter("E")
private val mapType = functionType(
    staticParameters = listOf(mapFromType, mapToType, mapEffectParameter),
    positionalArguments = listOf(
        functionType(
            positionalArguments = listOf(mapFromType),
            effect = mapEffectParameter,
            returns = mapToType
        ),
        applyType(ListType, listOf(mapFromType))
    ),
    effect = mapEffectParameter,
    returns = applyType(ListType, listOf(mapToType))
)

private val forEachTypeParameter = TypeParameter("T", variance = Variance.INVARIANT)
private val forEachEffectParameter = EffectParameter("E")
private val forEachType = functionType(
    staticParameters = listOf(forEachTypeParameter, forEachEffectParameter),
    positionalArguments = listOf(
        functionType(
            positionalArguments = listOf(forEachTypeParameter),
            effect = forEachEffectParameter,
            returns = UnitType
        ),
        applyType(ListType, listOf(forEachTypeParameter))
    ),
    effect = forEachEffectParameter,
    returns = UnitType
)

internal val builtins = listOf(
    Builtin("Any", MetaType(AnyType)),
    Builtin("Unit", MetaType(UnitType)),
    Builtin("Int", MetaType(IntType)),
    Builtin("String", MetaType(StringType)),
    Builtin("Bool", MetaType(BoolType)),
    Builtin("List", MetaType(ListType)),

    Builtin("!io", EffectType(IoEffect)),

    Builtin("print", FunctionType(
        staticParameters = listOf(),
        positionalArguments = listOf(StringType),
        namedArguments = mapOf(),
        effect = IoEffect,
        returns = UnitType
    )),
    Builtin("intToString", positionalFunctionType(listOf(IntType), StringType)),
    Builtin("list", ListConstructorType),
    Builtin("map", mapType),
    Builtin("forEach", forEachType)
)
