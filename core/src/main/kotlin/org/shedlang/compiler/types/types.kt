package org.shedlang.compiler.types

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.freshNodeId


interface StaticValue {
    val shortDescription: String
    fun <T> acceptStaticValueVisitor(visitor: Visitor<T>): T

    interface Visitor<T> {
        fun visit(effect: Effect): T
        fun visit(type: Type): T
    }
}

interface Effect: StaticValue {
    override fun <T> acceptStaticValueVisitor(visitor: StaticValue.Visitor<T>): T {
        return visitor.visit(this)
    }
}

object EmptyEffect : Effect{
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

object StaticValueTypeGroup: TypeGroup {
    override val shortDescription: String
        get() = "static value"
}

interface Type: StaticValue, TypeGroup {
    override fun <T> acceptStaticValueVisitor(visitor: StaticValue.Visitor<T>): T {
        return visitor.visit(this)
    }
}

interface BasicType : Type

object UnitType: BasicType {
    override val shortDescription = "Unit"
}
object BoolType : BasicType {
    override val shortDescription = "Bool"
}
object IntType : BasicType{
    override val shortDescription = "Int"
}
object CharType : BasicType {
    override val shortDescription = "Char"
}
object StringType : BasicType{
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
    val name: Identifier,
    val tagFieldId: Int = freshTagFieldId()
)

data class TagValue(
    val tagField: TagField,
    val tagValueId: Int
)

interface StaticParameter: StaticValue {
    val name: Identifier

    fun <T> accept(visitor: Visitor<T>): T

    interface Visitor<T> {
        fun visit(parameter: TypeParameter): T
        fun visit(parameter: EffectParameter): T
    }
}

data class TypeParameter(
    override val name: Identifier,
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
            return prefix + name.value
        }

    override fun <T> accept(visitor: StaticParameter.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class EffectParameter(
    override val name: Identifier,
    val staticParameterId: Int = freshEffectParameterId()
): StaticParameter, Effect {
    override val shortDescription: String
        get() = name.value

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
    val parameters: List<StaticParameter>,
    val type: Type
): Type {
    override val shortDescription: String
    // TODO: should be something like (T, U) => Shape[T, U]
        get() = "TypeFunction(TODO)"
}

interface HasFieldsType : Type {
    val fields: Map<Identifier, Type>
}

data class ModuleType(
    override val fields: Map<Identifier, Type>
): HasFieldsType {
    override val shortDescription: String
    // TODO: should include name of module
        get() = "ModuleType(TODO)"
}

data class FunctionType(
    val staticParameters: List<StaticParameter>,
    val positionalParameters: List<Type>,
    val namedParameters: Map<Identifier, Type>,
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
                .map({ (name, type) -> "${name.value}: ${type.shortDescription}" })
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
    val name: Identifier
    val shapeId: Int
    val staticParameters: List<StaticParameter>
    val staticArguments: List<StaticValue>
    val tagValue: TagValue?
}

data class LazyShapeType(
    override val name: Identifier,
    private val getFields: Lazy<Map<Identifier, Type>>,
    override val shapeId: Int = freshShapeId(),
    override val staticParameters: List<StaticParameter>,
    override val staticArguments: List<StaticValue>,
    override val declaredTagField: TagField?,
    private val getTagValue: Lazy<TagValue?>
): ShapeType {
    override val shortDescription: String
        get() = if (staticArguments.isEmpty()) {
            name.value
        } else {
            appliedTypeShortDescription(name, staticArguments)
        }
    override val fields: Map<Identifier, Type> by getFields
    override val tagValue: TagValue? by getTagValue
}

interface UnionType: Type, MayDeclareTagField {
    val name: Identifier
    val members: List<ShapeType>
    val staticArguments: List<StaticValue>
    override val declaredTagField: TagField
}


data class AnonymousUnionType(
    override val name: Identifier = Identifier("_Union" + freshAnonymousTypeId()),
    override val members: List<ShapeType>,
    override val declaredTagField: TagField
): UnionType {
    override val staticArguments: List<StaticValue>
        get() = listOf()

    override val shortDescription: String
        get() = name.value
}

data class LazyUnionType(
    override val name: Identifier,
    private val getMembers: Lazy<List<ShapeType>>,
    override val declaredTagField: TagField,
    override val staticArguments: List<StaticValue>
): UnionType {
    override val shortDescription: String
        get() = if (staticArguments.isEmpty()) {
            name.value
        } else {
            appliedTypeShortDescription(name, staticArguments)
        }

    override val members: List<ShapeType> by getMembers
}

object ListConstructorType : Type {
    override val shortDescription: String
        get() = "ListConstructor"
}
val listTypeParameter = covariantTypeParameter("T")
val listTypeShapeId = freshShapeId()
val ListType = TypeFunction(
    parameters = listOf(listTypeParameter),
    type = LazyShapeType(
        shapeId = listTypeShapeId,
        name = Identifier("List"),
        staticParameters = listOf(listTypeParameter),
        staticArguments = listOf(listTypeParameter),
        getFields = lazy({ mapOf<Identifier, Type>() }),
        declaredTagField = null,
        getTagValue = lazy { null }
    )
)

fun functionType(
    staticParameters: List<StaticParameter> = listOf(),
    positionalParameters: List<Type> = listOf(),
    namedParameters: Map<Identifier, Type> = mapOf(),
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

fun invariantTypeParameter(name: String) = TypeParameter(Identifier(name), variance = Variance.INVARIANT)
fun covariantTypeParameter(name: String) = TypeParameter(Identifier(name), variance = Variance.COVARIANT)
fun contravariantTypeParameter(name: String) = TypeParameter(Identifier(name), variance = Variance.CONTRAVARIANT)

fun effectParameter(name: String) = EffectParameter(Identifier(name))


private fun appliedTypeShortDescription(name: Identifier, parameters: List<StaticValue>): String {
    val parametersString = parameters.joinToString(separator = ", ", transform = { type -> type.shortDescription })
    return name.value + "[" + parametersString + "]"
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
                return ValidateTypeResult(listOf("union member did not have tag value for " + type.declaredTagField.name.value))
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
