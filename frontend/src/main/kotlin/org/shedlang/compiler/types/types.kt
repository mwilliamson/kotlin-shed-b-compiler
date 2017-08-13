package org.shedlang.compiler.types

import org.shedlang.compiler.typechecker.canCoerce


interface Effect {
    val shortDescription: String
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

private var nextAnonymousTypeId = 0
fun freshAnonymousTypeId() = nextAnonymousTypeId++

private var nextShapeId = 0
fun freshShapeId() = nextShapeId++

data class TypeParameter(
    val name: String,
    val variance: Variance,
    val typeParameterId: Int = freshTypeParameterId()
): Type {
    override val shortDescription: String
        get() {
            val prefix = when (variance) {
                Variance.INVARIANT -> ""
                Variance.COVARIANT -> "+"
                Variance.CONTRAVARIANT  -> "-"
            }
            return prefix + name
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
    val typeParameters: List<TypeParameter>,
    val positionalArguments: List<Type>,
    val namedArguments: Map<String, Type>,
    val returns: Type,
    val effects: Set<Effect>
): Type {
    override val shortDescription: String
        get() {
            val typeParameters = if (typeParameters.isEmpty()) {
                ""
            } else {
                val typeParameterStrings = typeParameters
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

            val effects = effects
                .sortedBy({ effect -> effect.shortDescription })
                .map({ effect -> " " + effect.shortDescription })
                .joinToString(",")

            return "${typeParameters}(${arguments})${effects} -> ${returns.shortDescription}"
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

fun functionType(
    typeParameters: List<TypeParameter> = listOf(),
    positionalArguments: List<Type> = listOf(),
    namedArguments: Map<String, Type> = mapOf(),
    returns: Type = UnitType,
    effects: Set<Effect> = setOf()
) = FunctionType(
    typeParameters = typeParameters,
    positionalArguments = positionalArguments,
    namedArguments = namedArguments,
    returns = returns,
    effects = effects
)

fun positionalFunctionType(arguments: List<Type>, returns: Type)
    = functionType(positionalArguments = arguments, returns = returns)

fun invariantTypeParameter(name: String) = TypeParameter(name, variance = Variance.INVARIANT)
fun covariantTypeParameter(name: String) = TypeParameter(name, variance = Variance.COVARIANT)
fun contravariantTypeParameter(name: String) = TypeParameter(name, variance = Variance.CONTRAVARIANT)

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
    return replaceTypes(receiver.type, typeMap)
}

internal fun replaceTypes(type: Type, typeMap: Map<TypeParameter, Type>): Type {
    if (type is TypeParameter) {
        return typeMap.getOrElse(type, { type })
    } else if (type is UnionType) {
        return LazyUnionType(
            type.name,
            lazy({
                type.members.map({ memberType -> replaceTypes(memberType, typeMap) })
            }),
            typeArguments = type.typeArguments.map({ typeArgument -> replaceTypes(typeArgument, typeMap) })
        )
    } else if (type is ShapeType) {
        return LazyShapeType(
            name = type.name,
            getFields = lazy({
                type.fields.mapValues({ field -> replaceTypes(field.value, typeMap) })
            }),
            shapeId = type.shapeId,
            typeParameters = type.typeParameters,
            typeArguments = type.typeArguments.map({ typeArgument -> replaceTypes(typeArgument, typeMap) })
        )
    } else if (type is FunctionType) {
        return FunctionType(
            positionalArguments = type.positionalArguments.map({ argument -> replaceTypes(argument, typeMap) }),
            namedArguments = type.namedArguments.mapValues({ argument -> replaceTypes(argument.value, typeMap) }),
            effects = type.effects,
            returns = replaceTypes(type.returns, typeMap),
            typeParameters = type.typeParameters
        )
    } else if (type is UnitType || type is BoolType || type is IntType || type is StringType) {
        return type
    } else {
        throw NotImplementedError("Type replacement not implemented for: " + type)
    }
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
