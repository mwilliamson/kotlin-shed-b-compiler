package org.shedlang.compiler.types

import org.shedlang.compiler.ast.freshNodeId


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

interface TypeGroup {
    val shortDescription: String
}

object UnionTypeGroup: TypeGroup {
    override val shortDescription: String
        get() = "union"
}

object MetaTypeGroup: TypeGroup {
    override val shortDescription: String
        get() = "meta-type"
}

interface Type: TypeGroup {
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

fun freshTagFieldId() = freshNodeId()

data class TagField(
    val name: String,
    val tagFieldId: Int = freshTagFieldId()
)

data class TagValue(
    val tagField: TagField,
    val tagValueId: Int
)

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
    val positionalParameters: List<Type>,
    val namedParameters: Map<String, Type>,
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

            val positionalParameterStrings = positionalParameters
                .map({ parameter -> parameter.shortDescription })
            val namedParameterStrings = namedParameters
                .asIterable()
                .sortedBy({ (name, _) -> name })
                .map({ (name, type) -> "${name}: ${type.shortDescription}" })
            val parameters = (positionalParameterStrings + namedParameterStrings)
                .joinToString(", ")

            val effect = if (effect == EmptyEffect) {
                ""
            } else {
                " " + effect.shortDescription
            }

            return "${typeParameters}(${parameters})${effect} -> ${returns.shortDescription}"
        }
}

interface MayDeclareTagField {
    val declaredTagField: TagField?
}

interface ShapeType: HasFieldsType, MayDeclareTagField {
    val name: String
    val shapeId: Int
    val typeParameters: List<TypeParameter>
    val typeArguments: List<Type>
    val tagValue: TagValue?
}

data class LazyShapeType(
    override val name: String,
    private val getFields: Lazy<Map<String, Type>>,
    override val shapeId: Int = freshShapeId(),
    override val typeParameters: List<TypeParameter>,
    override val typeArguments: List<Type>,
    override val declaredTagField: TagField?,
    private val getTagValue: Lazy<TagValue?>
): ShapeType {
    override val shortDescription: String
        get() = if (typeArguments.isEmpty()) {
            name
        } else {
            appliedTypeShortDescription(name, typeArguments)
        }
    override val fields: Map<String, Type> by getFields
    override val tagValue: TagValue? by getTagValue
}

interface UnionType: Type, MayDeclareTagField {
    val name: String
    val members: List<ShapeType>
    val typeArguments: List<Type>
    override val declaredTagField: TagField
}


data class AnonymousUnionType(
    override val name: String = "_Union" + freshAnonymousTypeId(),
    override val members: List<ShapeType>,
    override val declaredTagField: TagField
): UnionType {
    override val typeArguments: List<Type>
        get() = listOf()

    override val shortDescription: String
        get() = name
}

data class LazyUnionType(
    override val name: String,
    private val getMembers: Lazy<List<ShapeType>>,
    override val declaredTagField: TagField,
    override val typeArguments: List<Type>
): UnionType {
    override val shortDescription: String
        get() = if (typeArguments.isEmpty()) {
            name
        } else {
            appliedTypeShortDescription(name, typeArguments)
        }

    override val members: List<ShapeType> by getMembers
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
        getFields = lazy({ mapOf<String, Type>() }),
        declaredTagField = null,
        getTagValue = lazy { null }
    )
)

fun functionType(
    staticParameters: List<StaticParameter> = listOf(),
    positionalParameters: List<Type> = listOf(),
    namedParameters: Map<String, Type> = mapOf(),
    returns: Type = UnitType,
    effect: Effect = EmptyEffect
) = FunctionType(
    staticParameters = staticParameters,
    positionalParameters = positionalParameters,
    namedParameters = namedParameters,
    returns = returns,
    effect = effect
)

fun positionalFunctionType(parameters: List<Type>, returns: Type)
    = functionType(positionalParameters = parameters, returns = returns)

fun invariantTypeParameter(name: String) = TypeParameter(name, variance = Variance.INVARIANT)
fun covariantTypeParameter(name: String) = TypeParameter(name, variance = Variance.COVARIANT)
fun contravariantTypeParameter(name: String) = TypeParameter(name, variance = Variance.CONTRAVARIANT)

fun effectParameter(name: String) = EffectParameter(name)


private fun appliedTypeShortDescription(name: String, parameters: List<Type>): String {
    val parametersString = parameters.joinToString(separator = ", ", transform = { type -> type.shortDescription })
    return name + "[" + parametersString + "]"
}

data class ValidateTypeResult(val errors: List<String>) {
    companion object {
        val success = ValidateTypeResult(listOf())
    }
}

fun validateType(type: Type): ValidateTypeResult {
    if (type is BasicType || type == AnyType || type == NothingType || type is TypeParameter) {
        return ValidateTypeResult.success
    } else if (type is FunctionType) {
        if (type.returns is TypeParameter && type.returns.variance == Variance.CONTRAVARIANT) {
            return ValidateTypeResult(listOf("return type cannot be contravariant"))
        } else {
            val parameterTypes = type.positionalParameters + type.namedParameters.values
            return ValidateTypeResult(parameterTypes.mapNotNull({ parameterType ->
                if (parameterType is TypeParameter && parameterType.variance == Variance.COVARIANT) {
                    "parameter type cannot be covariant"
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
        for (member in type.members) {
            if (!hasTagValueFor(member, type.declaredTagField)) {
                return ValidateTypeResult(listOf("union member did not have tag value for " + type.declaredTagField.name))
            }
        }
        return ValidateTypeResult.success
    } else if (type is TypeFunction) {
        return validateType(type.type)
    } else {
        throw NotImplementedError("not implemented for type: ${type.shortDescription}")
    }
}

fun hasTagValueFor(type: Type, tagField: TagField): Boolean {
    // TODO: handle unions
    if (type is ShapeType) {
        val tagValue = type.tagValue
        return tagValue != null && return tagValue.tagField == tagField
    } else {
        return false
    }
}
