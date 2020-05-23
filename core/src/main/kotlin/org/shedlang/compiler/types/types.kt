package org.shedlang.compiler.types

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.ast.freshNodeId


interface StaticValue {
    val shortDescription: String
    fun <T> acceptStaticValueVisitor(visitor: Visitor<T>): T

    interface Visitor<T> {
        fun visit(effect: Effect): T
        fun visit(value: ParameterizedStaticValue): T
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

data class OpaqueEffect(
    val definitionId: Int,
    val name: Identifier,
    val arguments: List<StaticValue>
): Effect {
    override val shortDescription: String
        get() = "!${name.value}${staticArgumentsString(arguments)}"
}

data class ComputationalEffect(
    val definitionId: Int,
    val name: Identifier,
    val operations: Map<Identifier, FunctionType>
): Effect {
    override val shortDescription: String
        get() = "!${name.value}"
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
    fun fieldType(fieldName: Identifier): Type?

    override fun <T> acceptStaticValueVisitor(visitor: StaticValue.Visitor<T>): T {
        return visitor.visit(this)
    }
}

interface BasicType : Type {
    override fun fieldType(fieldName: Identifier): Type? {
        return null
    }
}

object UnitType: BasicType {
    override val shortDescription = "Unit"
}
object BoolType : BasicType {
    override val shortDescription = "Bool"
}
object IntType : BasicType{
    override val shortDescription = "Int"
}
object UnicodeScalarType : BasicType {
    override val shortDescription = "UnicodeScalar"
}
object StringType : BasicType {
    override val shortDescription = "String"
}
object StringSliceType : BasicType {
    override val shortDescription = "StringSlice"
}

object AnyType : Type {
    override fun fieldType(fieldName: Identifier): Type? = null

    override val shortDescription = "Any"
}

object NothingType : Type {
    override fun fieldType(fieldName: Identifier): Type? = null

    override val shortDescription = "Nothing"
}

data class StaticValueType(val value: StaticValue): Type {
    private val fieldsType: Type?

    init {
        // TODO: better handling of generics
        val rawType = rawValue(value)
        fieldsType = if (rawType is ShapeType) {
            shapeFieldsInfoType(rawType)
        } else {
            null
        }
    }

    override val shortDescription: String
        get() = "StaticValue[${value.shortDescription}]"

    override fun fieldType(fieldName: Identifier): Type? {
        if (fieldName == Identifier("fields")) {
            return fieldsType
        } else {
            return null
        }
    }
}

fun effectType(effect: Effect) = StaticValueType(effect)
fun metaType(type: Type) = StaticValueType(type)

private fun shapeFieldsInfoType(type: ShapeType): Type {
    val shapeId = freshTypeId()
    return lazyShapeType(
        shapeId = shapeId,
        name = Identifier("Fields"),
        tagValue = null,
        staticParameters = listOf(),
        staticArguments = listOf(),
        getFields = lazy {
            type.fields.values.map { field ->
                Field(
                    shapeId = shapeId,
                    name = field.name,
                    type = shapeFieldInfoType(type, field),
                    isConstant = false
                )
            }
        }
    )
}

val shapeFieldTypeFunctionTypeParameter = covariantTypeParameter("Type")
val shapeFieldTypeFunctionFieldParameter = covariantTypeParameter("Field")
val shapeFieldTypeFunctionShapeId = freshTypeId()
val ShapeFieldTypeFunction = ParameterizedStaticValue(
    parameters = listOf(shapeFieldTypeFunctionTypeParameter, shapeFieldTypeFunctionFieldParameter),
    value = lazyShapeType(
        shapeId = shapeFieldTypeFunctionShapeId,
        name = Identifier("ShapeField"),
        tagValue = null,
        staticParameters = listOf(shapeFieldTypeFunctionTypeParameter, shapeFieldTypeFunctionFieldParameter),
        staticArguments = listOf(shapeFieldTypeFunctionTypeParameter, shapeFieldTypeFunctionFieldParameter),
        getFields = lazy {
            listOf(
                Field(
                    shapeId = shapeFieldTypeFunctionShapeId,
                    name = Identifier("get"),
                    type = functionType(
                        positionalParameters = listOf(shapeFieldTypeFunctionTypeParameter),
                        returns = shapeFieldTypeFunctionFieldParameter
                    ),
                    isConstant = false
                ),
                Field(
                    shapeId = shapeFieldTypeFunctionShapeId,
                    name = Identifier("name"),
                    type = StringType,
                    isConstant = false
                )
            )
        }
    )
)

private fun shapeFieldInfoType(type: Type, field: Field): Type {
    return applyStatic(ShapeFieldTypeFunction, listOf(type, field.type)) as Type
}

fun metaTypeToType(type: Type): Type? {
    if (type is StaticValueType) {
        return type.value as? Type
    } else {
        return null
    }
}

fun rawValue(value: StaticValue): StaticValue {
    return when (value) {
        is ParameterizedStaticValue -> value.value
        else -> value
    }
}

private var nextEffectParameterId = 0
fun freshEffectParameterId() = nextEffectParameterId++

fun freshTypeId() = freshNodeId()

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
    val typeParameterId: Int = freshTypeId()
): StaticParameter, Type {
    override fun fieldType(fieldName: Identifier): Type? = null

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

    fun fresh(): TypeParameter {
        return TypeParameter(
            name = name,
            variance = variance
        )
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

    fun fresh(): EffectParameter {
        return EffectParameter(
            name = name
        )
    }
}

enum class Variance {
    INVARIANT,
    COVARIANT,
    CONTRAVARIANT
}

data class ParameterizedStaticValue(
    val parameters: List<StaticParameter>,
    val value: StaticValue
): StaticValue {
    override val shortDescription: String
    // TODO: should be something like (T, U) => Shape[T, U]
        get() = "TypeFunction(TODO)"

    override fun <T> acceptStaticValueVisitor(visitor: StaticValue.Visitor<T>): T {
        throw UnsupportedOperationException("not implemented")
    }
}

data class ModuleType(
    val fields: Map<Identifier, Type>
): Type {
    override fun fieldType(fieldName: Identifier): Type? {
        return fields[fieldName]
    }

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
    override fun fieldType(fieldName: Identifier): Type? = null

    override val shortDescription: String
        get() {
            val typeParameters = staticArgumentsString(staticParameters)

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

private fun staticArgumentsString(values: List<StaticValue>): String {
    val typeParameters = if (values.isEmpty()) {
        ""
    } else {
        val typeParameterStrings = values
            .map({ parameter -> parameter.shortDescription })
            .joinToString(", ")
        "[${typeParameterStrings}]"
    }
    return typeParameters
}

data class TupleType(val elementTypes: List<Type>): Type {
    override val shortDescription: String
        get() = "#(${elementTypes.map(Type::shortDescription).joinToString(", ")})"

    override fun fieldType(fieldName: Identifier): Type? {
        return null
    }

}

interface TypeAlias: Type {
    val name: Identifier
    val aliasedType: Type

    override fun fieldType(fieldName: Identifier): Type? {
        return aliasedType.fieldType(fieldName)
    }
}

fun unalias(initialType: Type): Type {
    var type = initialType
    while (type is TypeAlias) {
        type = type.aliasedType
    }
    return type
}

data class LazyTypeAlias(
    override val name: Identifier,
    private val getAliasedType: Lazy<Type>
): TypeAlias {
    override val aliasedType: Type
        get() = getAliasedType.value

    override val shortDescription: String
        get() = name.value
}

data class Tag(val moduleName: ModuleName, val name: Identifier)
data class TagValue(val tag: Tag, val value: Identifier)

interface ShapeType: Type {
    val name: Identifier
    val shapeId: Int
    val tagValue: TagValue?
    val fields: Map<Identifier, Field>
    val staticParameters: List<StaticParameter>
    val staticArguments: List<StaticValue>

    override fun fieldType(fieldName: Identifier): Type? {
        return fields[fieldName]?.type
    }
}

data class Field(
    val shapeId: Int,
    val name: Identifier,
    val type: Type,
    val isConstant: Boolean
) {
    fun mapType(func: (Type) -> Type): Field = Field(
        shapeId = shapeId,
        name = name,
        type = func(type),
        isConstant = isConstant
    )
}

fun lazyShapeType(
    shapeId: Int,
    name: Identifier,
    tagValue: TagValue?,
    getFields: Lazy<List<Field>>,
    staticParameters: List<StaticParameter>,
    staticArguments: List<StaticValue>
) = LazyShapeType(
    shapeId = shapeId,
    name = name,
    getFields = lazy {
        getFields.value.associateBy { field -> field.name }
    },
    tagValue = tagValue,
    staticParameters = staticParameters,
    staticArguments = staticArguments
)

data class LazyShapeType(
    override val name: Identifier,
    private val getFields: Lazy<Map<Identifier, Field>>,
    override val shapeId: Int = freshTypeId(),
    override val tagValue: TagValue?,
    override val staticParameters: List<StaticParameter>,
    override val staticArguments: List<StaticValue>
): ShapeType {
    override val shortDescription: String
        get() = if (staticArguments.isEmpty()) {
            name.value
        } else {
            appliedTypeShortDescription(name, staticArguments)
        }
    override val fields: Map<Identifier, Field> by getFields
}

interface UnionType: Type {
    val name: Identifier
    val tag: Tag
    val members: List<Type>
    val staticArguments: List<StaticValue>

    override fun fieldType(fieldName: Identifier): Type? = null
}


data class AnonymousUnionType(
    override val tag: Tag,
    override val name: Identifier = Identifier("_Union" + freshTypeId()),
    override val members: List<Type>
): UnionType {
    override val staticArguments: List<StaticValue>
        get() = listOf()

    override val shortDescription: String
        get() = members.joinToString(" | ") {
            member -> member.shortDescription
        }
}

fun union(left: Type, right: Type): Type {
    if (canCoerce(from = right, to = left)) {
        return left
    } else if (canCoerce(from = left, to = right)) {
        return right
    } else {
        fun findMembers(type: Type): List<Type> {
            return when (type) {
                is UnionType -> type.members
                else -> listOf(type)
            }
        }

        val leftMembers = findMembers(left)
        val rightMembers = findMembers(right)
        // TODO: test this, handle failure
        val members = (leftMembers + rightMembers).distinct().map { member -> member as ShapeType }
        // TODO: test this, handle failure
        val tag = members.map { member -> member.tagValue!!.tag }.distinct().single()

        return AnonymousUnionType(
            tag = tag,
            members = members
        )
    }
}

fun unionAll(members: List<Type>) = members.reduce(::union)

data class LazyUnionType(
    override val tag: Tag,
    override val name: Identifier,
    private val getMembers: Lazy<List<Type>>,
    override val staticArguments: List<StaticValue>
): UnionType {
    override val shortDescription: String
        get() = if (staticArguments.isEmpty()) {
            name.value
        } else {
            appliedTypeShortDescription(name, staticArguments)
        }

    override val members: List<Type> by getMembers
}

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

data class VarargsType(val name: Identifier, val cons: FunctionType, val nil: Type): Type {
    override val shortDescription: String
        get() = "varargs $name(${cons.shortDescription}, ${nil.shortDescription})"

    override fun fieldType(fieldName: Identifier): Type? {
        return null
    }
}

fun invariantTypeParameter(name: String) = TypeParameter(Identifier(name), variance = Variance.INVARIANT)
fun covariantTypeParameter(name: String) = TypeParameter(Identifier(name), variance = Variance.COVARIANT)
fun contravariantTypeParameter(name: String) = TypeParameter(Identifier(name), variance = Variance.CONTRAVARIANT)

fun effectParameter(name: String) = EffectParameter(Identifier(name))

object CastType : BasicType {
    override val shortDescription = "Cast"
}

private fun appliedTypeShortDescription(name: Identifier, parameters: List<StaticValue>): String {
    val parametersString = parameters.joinToString(separator = ", ", transform = { type -> type.shortDescription })
    return name.value + "[" + parametersString + "]"
}

data class ValidateTypeResult(val errors: List<String>) {
    companion object {
        val success = ValidateTypeResult(listOf())
    }
}

fun validateStaticValue(value: StaticValue): ValidateTypeResult {
    if (value is BasicType || value == AnyType || value == NothingType || value is TypeParameter) {
        return ValidateTypeResult.success
    } else if (value is FunctionType) {
        if (value.returns is TypeParameter && value.returns.variance == Variance.CONTRAVARIANT) {
            return ValidateTypeResult(listOf("return type cannot be contravariant"))
        } else {
            val parameterTypes = value.positionalParameters + value.namedParameters.values
            return ValidateTypeResult(parameterTypes.mapNotNull({ parameterType ->
                if (parameterType is TypeParameter && parameterType.variance == Variance.COVARIANT) {
                    "parameter type cannot be covariant"
                } else {
                    null
                }
            }))
        }
    } else if (value is ShapeType) {
        return ValidateTypeResult(value.fields.mapNotNull({ field ->
            val fieldType = field.value.type
            if (fieldType is TypeParameter && fieldType.variance == Variance.CONTRAVARIANT) {
                "field type cannot be contravariant"
            } else {
                null
            }
        }))
    } else if (value is UnionType) {
        return ValidateTypeResult.success
    } else if (value is ParameterizedStaticValue) {
        return validateStaticValue(value.value)
    } else {
        throw NotImplementedError("not implemented for static value: ${value.shortDescription}")
    }
}

fun applyStatic(receiver: ParameterizedStaticValue, arguments: List<StaticValue>): StaticValue {
    val bindings = receiver.parameters.zip(arguments).toMap()
    return replaceStaticValues(receiver.value, bindings = bindings)
}

typealias StaticBindings = Map<StaticParameter, StaticValue>

private fun replaceStaticValues(value: StaticValue, bindings: StaticBindings): StaticValue {
    return value.acceptStaticValueVisitor(object : StaticValue.Visitor<StaticValue> {
        override fun visit(effect: Effect): StaticValue {
            return replaceEffects(effect, bindings)
        }

        override fun visit(value: ParameterizedStaticValue): StaticValue {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(type: Type): StaticValue {
            return replaceStaticValuesInType(type, bindings)
        }
    })
}

fun replaceStaticValuesInType(type: Type, bindings: StaticBindings): Type {
    if (type is TypeParameter) {
        // TODO: handle non-type bindings
        return bindings.getOrElse(type, { type }) as Type
    } else if (type is UnionType) {
        return LazyUnionType(
            type.tag,
            type.name,
            lazy({
                type.members.map({ memberType -> replaceStaticValuesInType(memberType, bindings) as ShapeType })
            }),
            staticArguments = type.staticArguments.map({ argument -> replaceStaticValues(argument, bindings) })
        )
    } else if (type is ShapeType) {
        return LazyShapeType(
            name = type.name,
            getFields = lazy({
                type.fields.mapValues{ field -> field.value.mapType { type ->
                    replaceStaticValuesInType(field.value.type, bindings)
                } }
            }),
            tagValue = type.tagValue,
            shapeId = type.shapeId,
            staticParameters = type.staticParameters,
            staticArguments = type.staticArguments.map({ argument -> replaceStaticValues(argument, bindings) })
        )
    } else if (type is FunctionType) {
        return FunctionType(
            positionalParameters = type.positionalParameters.map({ parameter -> replaceStaticValuesInType(parameter, bindings) }),
            namedParameters = type.namedParameters.mapValues({ parameter -> replaceStaticValuesInType(parameter.value, bindings) }),
            effect = replaceEffects(type.effect, bindings),
            returns = replaceStaticValuesInType(type.returns, bindings),
            staticParameters = type.staticParameters
        )
    } else if (type is TupleType) {
        return TupleType(
            elementTypes = type.elementTypes.map { elementType ->
                replaceStaticValuesInType(elementType, bindings)
            }
        )
    } else if (type is UnitType || type is BoolType || type is IntType || type is StringType || type is StringSliceType || type is UnicodeScalarType || type is AnyType || type is NothingType || type is TypeAlias) {
        return type
    } else {
        throw NotImplementedError("Type replacement not implemented for: " + type)
    }
}

public fun replaceEffects(effect: Effect, bindings: Map<StaticParameter, StaticValue>): Effect {
    when (effect) {
        is EffectParameter ->
            // TODO: handle non-effect bindings
            return bindings.getOrElse(effect, { effect }) as Effect

        is OpaqueEffect ->
            return OpaqueEffect(
                definitionId = effect.definitionId,
                name = effect.name,
                arguments = effect.arguments.map { argument -> replaceStaticValues(argument, bindings) }
            )

        else ->
            return effect
    }
}

data class Discriminator(
    val tagValue: TagValue,
    val targetType: Type
)

fun findDiscriminator(sourceType: Type, targetType: StaticValue): Discriminator? {
    // TODO: handle generics

    if (sourceType !is UnionType) {
        return null
    }

    val tagValue = (rawValue(targetType) as? ShapeType)?.tagValue
    if (tagValue?.tag != sourceType.tag) {
        return null
    }

    val matchingMembers = sourceType.members.filter { member -> (member as ShapeType).tagValue == tagValue }
    val refinedType = unionAll(matchingMembers)

    if (targetType is ShapeType) {
        if (!canCoerce(from = refinedType, to = targetType)) {
            return null
        }
    } else if (targetType is ParameterizedStaticValue) {
        val targetTypeValue = targetType.value
        if (targetTypeValue !is Type || !canCoerce(from = refinedType, to = targetTypeValue, freeParameters = targetType.parameters.toSet())) {
            return null
        }
    } else {
        return null
    }

    return Discriminator(tagValue = tagValue, targetType = refinedType)
}
