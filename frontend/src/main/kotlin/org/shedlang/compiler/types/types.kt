package org.shedlang.compiler.types

import org.shedlang.compiler.typechecker.canCoerce


interface Effect {
    val shortDescription: String
}

object EmptyEffect : Effect {
    override val shortDescription: String
        get() = "!Empty"
}

object IoEffect : Effect {
    override val shortDescription: String
        get() = "!Io"
}

interface Type {
    val shortDescription: String
}

interface BasicType : Type

object UnitType: BasicType {
    override val shortDescription = "Unit"
}
object BoolType : BasicType {
    override val shortDescription = "Bool"
}
object IntType : BasicType {
    override val shortDescription = "Int"
}
object StringType : BasicType {
    override val shortDescription = "String"
}
object AnyType : Type {
    override val shortDescription = "Any"
}
object NothingType : Type {
    override val shortDescription = "Nothing"
}
class MetaType(val type: Type): Type {
    override val shortDescription: String
        get() = "MetaType(${type.shortDescription})"
}
class EffectType(val effect: Effect): Type {
    override val shortDescription: String
        get() = "EffectType(${effect})"
}

private var nextTypeParameterId = 0
fun freshTypeParameterId() = nextTypeParameterId++

private var nextEffectParameterId = 0
fun freshEffectParameterId() = nextEffectParameterId++

private var nextAnonymousTypeId = 0
fun freshAnonymousTypeId() = nextAnonymousTypeId++

private var nextShapeId = 0
fun freshShapeId() = nextShapeId++

interface StaticParameter {
    val name: String
    val shortDescription: String

    fun <T> accept(visitor: Visitor<T>): T

    interface Visitor<T> {
        fun visit(parameter: TypeParameter): T
        fun visit(parameter: EffectParameter): T
    }
}

data class TypeParameter(
    override val name: String,
    val variance: Variance,
    val typeParameterId: Int = freshTypeParameterId()
): StaticParameter, Type {
    override val shortDescription: String
        get() {
            val prefix = when (variance) {
                Variance.INVARIANT -> ""
                Variance.COVARIANT -> "+"
                Variance.CONTRAVARIANT  -> "-"
            }
            return prefix + name
        }

    override fun <T> accept(visitor: StaticParameter.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class EffectParameter(
    override val name: String,
    val staticParameterId: Int = freshEffectParameterId()
): StaticParameter, Effect {
    override val shortDescription: String
        get() = name

    override fun <T> accept(visitor: StaticParameter.Visitor<T>): T {
        return visitor.visit(this)
    }
}

enum class Variance {
    INVARIANT,
    COVARIANT,
    CONTRAVARIANT
}

data class TypeFunction (
    val parameters: List<TypeParameter>,
    val type: Type
): Type {
    override val shortDescription: String
    // TODO: should be something like (T, U) => Shape[T, U]
        get() = "TypeFunction(TODO)"
}

interface HasFieldsType : Type {
    val fields: Map<String, Type>
}

data class ModuleType(
    override val fields: Map<String, Type>
): HasFieldsType {
    override val shortDescription: String
    // TODO: should include name of module
        get() = "ModuleType(TODO)"
}

data class FunctionType(
    val staticParameters: List<StaticParameter>,
    val positionalArguments: List<Type>,
    val namedArguments: Map<String, Type>,
    val returns: Type,
    val effect: Effect
): Type {
    override val shortDescription: String
        get() {
            val typeParameters = if (staticParameters.isEmpty()) {
                ""
            } else {
                val typeParameterStrings = staticParameters
                    .map({ parameter -> parameter.shortDescription })
                    .joinToString(", ")
                "[${typeParameterStrings}]"
            }

            val positionalArgumentStrings = positionalArguments
                .map({ argument -> argument.shortDescription })
            val namedArgumentStrings = namedArguments
                .asIterable()
                .sortedBy({ (name, _) -> name })
                .map({ (name, type) -> "${name}: ${type.shortDescription}" })
            val arguments = (positionalArgumentStrings + namedArgumentStrings)
                .joinToString(", ")

            val effect = if (effect == EmptyEffect) {
                ""
            } else {
                " " + effect.shortDescription
            }

            return "${typeParameters}(${arguments})${effect} -> ${returns.shortDescription}"
        }
}

interface ShapeType: HasFieldsType {
    val name: String
    val shapeId: Int
    val typeParameters: List<TypeParameter>
    val typeArguments: List<Type>
}

data class LazyShapeType(
    override val name: String,
    val getFields: Lazy<Map<String, Type>>,
    override val shapeId: Int = freshShapeId(),
    override val typeParameters: List<TypeParameter>,
    override val typeArguments: List<Type>
): ShapeType {
    override val shortDescription: String
        get() = if (typeArguments.isEmpty()) {
            name
        } else {
            appliedTypeShortDescription(name, typeArguments)
        }
    override val fields: Map<String, Type> by getFields
}

interface UnionType: Type {
    val name: String
    val members: List<Type>
    val typeArguments: List<Type>
}

data class SimpleUnionType(
    override val name: String,
    override val members: List<Type>
): UnionType {
    override val shortDescription: String
        get() = name

    override val typeArguments: List<Type>
        get() = listOf()
}


data class AnonymousUnionType(
    override val name: String = "_Union" + freshAnonymousTypeId(),
    override val members: List<Type>
): UnionType {
    override val typeArguments: List<Type>
        get() = listOf()

    override val shortDescription: String
        get() = name
}

data class LazyUnionType(
    override val name: String,
    private val getMembers: Lazy<List<Type>>,
    override val typeArguments: List<Type>
): UnionType {
    override val shortDescription: String
        get() = if (typeArguments.isEmpty()) {
            name
        } else {
            appliedTypeShortDescription(name, typeArguments)
        }

    override val members: List<Type> by getMembers
}

object ListConstructorType : Type {
    override val shortDescription: String
        get() = "ListConstructor"
}
val listTypeParameter = TypeParameter("T", variance = Variance.COVARIANT)
val listTypeShapeId = freshShapeId()
val ListType = TypeFunction(
    parameters = listOf(listTypeParameter),
    type = LazyShapeType(
        shapeId = listTypeShapeId,
        name = "List",
        typeParameters = listOf(listTypeParameter),
        typeArguments = listOf(listTypeParameter),
        getFields = lazy({ mapOf<String, Type>() })
    )
)

fun functionType(
    staticParameters: List<StaticParameter> = listOf(),
    positionalArguments: List<Type> = listOf(),
    namedArguments: Map<String, Type> = mapOf(),
    returns: Type = UnitType,
    effect: Effect = EmptyEffect
) = FunctionType(
    staticParameters = staticParameters,
    positionalArguments = positionalArguments,
    namedArguments = namedArguments,
    returns = returns,
    effect = effect
)

fun positionalFunctionType(arguments: List<Type>, returns: Type)
    = functionType(positionalArguments = arguments, returns = returns)

fun invariantTypeParameter(name: String) = TypeParameter(name, variance = Variance.INVARIANT)
fun covariantTypeParameter(name: String) = TypeParameter(name, variance = Variance.COVARIANT)
fun contravariantTypeParameter(name: String) = TypeParameter(name, variance = Variance.CONTRAVARIANT)

fun effectParameter(name: String) = EffectParameter(name)

fun union(left: Type, right: Type): Type {
    if (canCoerce(from = right, to = left)) {
        return left
    } else if (canCoerce(from = left, to = right)) {
        return right
    } else if (left is AnonymousUnionType) {
        return AnonymousUnionType(members = left.members + right)
    } else if (right is AnonymousUnionType) {
        return AnonymousUnionType(members = listOf(left) + right.members)
    } else {
        return AnonymousUnionType(members = listOf(left, right))
    }
}


fun applyType(receiver: TypeFunction, arguments: List<Type>): Type {
    val typeMap = receiver.parameters.zip(arguments).toMap()
    return replaceTypes(receiver.type, StaticBindings(types = typeMap, effects = mapOf()))
}

internal class StaticBindings(
    val types: Map<TypeParameter, Type>,
    val effects: Map<EffectParameter, Effect> = mapOf()
)

internal fun replaceTypes(type: Type, bindings: StaticBindings): Type {
    if (type is TypeParameter) {
        return bindings.types.getOrElse(type, { type })
    } else if (type is UnionType) {
        return LazyUnionType(
            type.name,
            lazy({
                type.members.map({ memberType -> replaceTypes(memberType, bindings) })
            }),
            typeArguments = type.typeArguments.map({ typeArgument -> replaceTypes(typeArgument, bindings) })
        )
    } else if (type is ShapeType) {
        return LazyShapeType(
            name = type.name,
            getFields = lazy({
                type.fields.mapValues({ field -> replaceTypes(field.value, bindings) })
            }),
            shapeId = type.shapeId,
            typeParameters = type.typeParameters,
            typeArguments = type.typeArguments.map({ typeArgument -> replaceTypes(typeArgument, bindings) })
        )
    } else if (type is FunctionType) {
        return FunctionType(
            positionalArguments = type.positionalArguments.map({ argument -> replaceTypes(argument, bindings) }),
            namedArguments = type.namedArguments.mapValues({ argument -> replaceTypes(argument.value, bindings) }),
            effect = replaceEffects(type.effect, bindings),
            returns = replaceTypes(type.returns, bindings),
            staticParameters = type.staticParameters
        )
    } else if (type is UnitType || type is BoolType || type is IntType || type is StringType) {
        return type
    } else {
        throw NotImplementedError("Type replacement not implemented for: " + type)
    }
}

internal fun replaceEffects(effect: Effect, bindings: StaticBindings): Effect {
    return bindings.effects[effect] ?: effect
}

private fun appliedTypeShortDescription(name: String, arguments: List<Type>): String {
    val argumentsString = arguments.joinToString(separator = ", ", transform = { type -> type.shortDescription })
    return name + "[" + argumentsString + "]"
}

internal data class ValidateTypeResult(val errors: List<String>) {
    companion object {
        val success = ValidateTypeResult(listOf())
    }
}

internal fun validateType(type: Type): ValidateTypeResult {
    if (type is BasicType || type == AnyType || type == NothingType || type is TypeParameter) {
        return ValidateTypeResult.success
    } else if (type is FunctionType) {
        if (type.returns is TypeParameter && type.returns.variance == Variance.CONTRAVARIANT) {
            return ValidateTypeResult(listOf("return type cannot be contravariant"))
        } else {
            val argumentTypes = type.positionalArguments + type.namedArguments.values
            return ValidateTypeResult(argumentTypes.mapNotNull({ argumentType ->
                if (argumentType is TypeParameter && argumentType.variance == Variance.COVARIANT) {
                    "argument type cannot be covariant"
                } else {
                    null
                }
            }))
        }
    } else if (type is ShapeType) {
        return ValidateTypeResult(type.fields.mapNotNull({ field ->
            val fieldType = field.value
            if (fieldType is TypeParameter && fieldType.variance == Variance.CONTRAVARIANT) {
                "field type cannot be contravariant"
            } else {
                null
            }
        }))
    } else if (type is UnionType) {
        return ValidateTypeResult.success
    } else if (type is TypeFunction) {
        return validateType(type.type)
    } else {
        throw NotImplementedError("not implemented for type: ${type.shortDescription}")
    }
}
