package org.shedlang.compiler.types

import org.shedlang.compiler.CannotUnionTypesError
import org.shedlang.compiler.CompilerError
import org.shedlang.compiler.ast.*


interface StaticValue {
    val shortDescription: String
    fun <T> acceptStaticValueVisitor(visitor: Visitor<T>): T

    interface Visitor<T> {
        fun visit(effect: Effect): T
        fun visit(value: ParameterizedStaticValue): T
        fun visit(type: Type): T
        fun visit(type: EmptyTypeFunction): T
    }
}

interface Effect: StaticValue {
    override fun <T> acceptStaticValueVisitor(visitor: StaticValue.Visitor<T>): T {
        return visitor.visit(this)
    }
}

fun effectUnion(effects: List<Effect>): Effect {
    return effects.fold(EmptyEffect, ::effectUnion)
}

fun effectUnion(effect1: Effect, effect2: Effect): Effect {
    if (isSubEffect(subEffect = effect1, superEffect = effect2)) {
        return effect2
    } else if (isSubEffect(subEffect = effect2, superEffect = effect1)) {
        return effect1
    } else {
        fun findMembers(effect: Effect): List<Effect> {
            if (effect is EffectUnion) {
                return effect.members
            } else {
                return listOf(effect)
            }
        }

        return EffectUnion(members = findMembers(effect1) + findMembers(effect2))
    }
}

class EffectUnion(val members: List<Effect>) : Effect {
    override val shortDescription: String
        get() = members.joinToString(" | ") {
            member -> member.shortDescription
        }
}

fun effectMinus(effect1: Effect, effect2: Effect): Effect {
    if (effect2 == EmptyEffect) {
        return effect1
    } else if (isSubEffect(subEffect = effect1, superEffect = effect2)) {
        return EmptyEffect
    } else {
        // TODO
        throw UnsupportedOperationException()
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
    val name: Identifier
): Effect {
    override val shortDescription: String
        get() = "!${name.value}"
}

data class UserDefinedEffect(
    val definitionId: Int,
    val name: Identifier,
    private val getOperations: Lazy<Map<Identifier, FunctionType>>
): Effect {
    val operations: Map<Identifier, FunctionType> by getOperations

    override val shortDescription: String
        get() = "!${name.value}"

    override fun toString(): String {
        return "UserDefinedEffect(definitionId = $definitionId, name = $name)"
    }
}

interface TypeGroup {
    val shortDescription: String
}

object UnionTypeGroup: TypeGroup {
    override val shortDescription: String
        get() = "union"
}

object ShapeTypeGroup: TypeGroup {
    override val shortDescription: String
        get() = "shape"
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
    val shapeId: Int?
    fun fieldType(fieldName: Identifier): Type?
    fun replaceStaticValues(bindings: StaticBindings): Type

    override fun <T> acceptStaticValueVisitor(visitor: StaticValue.Visitor<T>): T {
        return visitor.visit(this)
    }

    fun <T> accept(visitor: Visitor<T>): T

    interface Visitor<T> {
        fun visit(type: BasicType): T
        fun visit(type: FunctionType): T
        fun visit(type: ModuleType): T
        fun visit(type: ShapeType): T
        fun visit(type: StaticValueType): T
        fun visit(type: TupleType): T
        fun visit(type: TypeAlias): T
        fun visit(type: TypeParameter): T
        fun visit(type: UnionType): T
        fun visit(type: UpdatedType): T
        fun visit(type: VarargsType): T
    }
}

interface BasicType : Type {
    override val shapeId: Int?
        get() = null

    override fun fieldType(fieldName: Identifier): Type? {
        return null
    }

    override fun replaceStaticValues(bindings: StaticBindings): Type {
        return this
    }

    override fun <T> accept(visitor: Type.Visitor<T>): T {
        return visitor.visit(this)
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

object AnyType : BasicType {
    override val shortDescription = "Any"
}

object NothingType : BasicType {
    override val shortDescription = "Nothing"
}

data class StaticValueType(val value: StaticValue): Type {
    private val fieldsType: Lazy<Type?>

    init {
        // TODO: better handling of generics
        val rawType = rawValue(value)
        fieldsType = lazy {
            if (rawType is ShapeType) {
                shapeFieldsInfoType(rawType)
            } else {
                null
            }
        }
    }

    override val shapeId: Int?
        get() = null

    override val shortDescription: String
        get() = "StaticValue[${value.shortDescription}]"

    override fun fieldType(fieldName: Identifier): Type? {
        if (value is UserDefinedEffect) {
            return value.operations[fieldName]
        } else if (fieldName == Identifier("fields")) {
            return fieldsType.value
        } else {
            return null
        }
    }

    override fun replaceStaticValues(bindings: StaticBindings): Type {
        throw UnsupportedOperationException("not implemented")
    }

    override fun <T> accept(visitor: Type.Visitor<T>): T {
        return visitor.visit(this)
    }
}

fun effectType(effect: Effect) = StaticValueType(effect)
fun metaType(type: Type) = StaticValueType(type)

private fun shapeFieldsInfoType(type: ShapeType): Type {
    val shapeId = freshTypeId()
    val fields = type.populatedFields.values.map { field ->
        Field(
            shapeId = shapeId,
            name = field.name,
            type = shapeFieldInfoType(type, field),
            isConstant = false
        )
    }
    return lazyCompleteShapeType(
        shapeId = shapeId,
        name = Identifier("Fields"),
        tagValue = null,
        staticParameters = listOf(),
        staticArguments = listOf(),
        getFields = lazy {
            fields
        },
    )
}

val shapeFieldTypeFunctionTypeParameter = covariantTypeParameter("Type")
val shapeFieldTypeFunctionFieldParameter = covariantTypeParameter("Field")
val shapeFieldTypeFunctionUpdateParameter = covariantTypeParameter("Update")
val shapeFieldTypeFunctionParameters = listOf(
    shapeFieldTypeFunctionTypeParameter,
    shapeFieldTypeFunctionFieldParameter,
    shapeFieldTypeFunctionUpdateParameter
)
val shapeFieldTypeFunctionShapeId = freshTypeId()
val shapeFieldTypeFunctionFields = listOf(
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
    ),
    Field(
        shapeId = shapeFieldTypeFunctionShapeId,
        name = Identifier("update"),
        type = shapeFieldTypeFunctionUpdateParameter,
        isConstant = false
    ),
)
val ShapeFieldTypeFunction = ParameterizedStaticValue(
    parameters = shapeFieldTypeFunctionParameters,
    value = lazyCompleteShapeType(
        shapeId = shapeFieldTypeFunctionShapeId,
        name = Identifier("ShapeField"),
        tagValue = null,
        staticParameters = shapeFieldTypeFunctionParameters,
        staticArguments = shapeFieldTypeFunctionParameters,
        getFields = lazy {
            shapeFieldTypeFunctionFields
        },
    )
)

private fun shapeFieldInfoType(shapeType: ShapeType, field: Field): Type {
    return applyStatic(
        ShapeFieldTypeFunction,
        listOf(shapeType, field.type, shapeFieldInfoUpdateType(shapeType = shapeType, field = field))
    ) as Type
}

private fun shapeFieldInfoUpdateType(shapeType: ShapeType, field: Field): Type {
    val typeParameter = TypeParameter(
        name = Identifier("T"),
        variance = Variance.INVARIANT,
        shapeId = field.shapeId,
        source = BuiltinSource,
    )
    return functionType(
        staticParameters = listOf(typeParameter),
        positionalParameters = listOf(field.type, typeParameter),
        returns = updatedType(baseType = typeParameter, shapeType = shapeType, field = field)
    )
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
    val source: Source

    fun fresh(): StaticParameter

    fun <T> accept(visitor: Visitor<T>): T

    interface Visitor<T> {
        fun visit(parameter: TypeParameter): T
        fun visit(parameter: EffectParameter): T
    }
}

data class TypeParameter(
    override val name: Identifier,
    val variance: Variance,
    override val shapeId: Int?,
    val typeParameterId: Int = freshTypeId(),
    override val source: Source,
): StaticParameter, Type {
    override fun fieldType(fieldName: Identifier): Type? = null

    override val shortDescription: String
        get() {
            // TODO: include shape
            val prefix = when (variance) {
                Variance.INVARIANT -> ""
                Variance.COVARIANT -> "+"
                Variance.CONTRAVARIANT  -> "-"
            }
            return prefix + name.value
        }

    override fun replaceStaticValues(bindings: StaticBindings): Type {
        return bindings.getOrElse(this, { this }) as Type
    }

    override fun <T> accept(visitor: StaticParameter.Visitor<T>): T {
        return visitor.visit(this)
    }

    override fun fresh(): TypeParameter {
        return TypeParameter(
            name = name,
            variance = variance,
            shapeId = shapeId,
            source = source,
        )
    }

    override fun <T> accept(visitor: Type.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class EffectParameter(
    override val name: Identifier,
    val staticParameterId: Int = freshEffectParameterId(),
    override val source: Source,
): StaticParameter, Effect {
    override val shortDescription: String
        get() = name.value

    override fun <T> accept(visitor: StaticParameter.Visitor<T>): T {
        return visitor.visit(this)
    }

    override fun fresh(): EffectParameter {
        return EffectParameter(
            name = name,
            source = source,
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
        return visitor.visit(this)
    }
}

object EmptyTypeFunction: StaticValue {
    override val shortDescription: String
        get() = "Empty"

    override fun <T> acceptStaticValueVisitor(visitor: StaticValue.Visitor<T>): T {
        return visitor.visit(this)
    }
}

fun createEmptyShapeType(argument: ShapeType): LazyShapeType {
    return createPartialShapeType(argument, populatedFieldNames = setOf())
}

fun createPartialShapeType(shapeType: ShapeType, populatedFieldNames: Set<Identifier>): LazyShapeType {
    return lazyShapeType(
        shapeId = shapeType.shapeId,
        name = shapeType.name,
        tagValue = shapeType.tagValue,
        getAllFields = lazy {
            shapeType.allFields.values.toList()
        },
        getPopulatedFieldNames = lazy { populatedFieldNames },
        staticParameters = shapeType.staticParameters,
        staticArguments = shapeType.staticArguments
    )
}

data class ModuleType(
    val name: ModuleName,
    val fields: Map<Identifier, Type>
): Type {
    override val shapeId: Int?
        get() = null

    override fun fieldType(fieldName: Identifier): Type? {
        return fields[fieldName]
    }

    override fun replaceStaticValues(bindings: StaticBindings): Type {
        throw UnsupportedOperationException("not implemented")
    }

    override val shortDescription: String
        get() = "module ${formatModuleName(name)}"

    override fun <T> accept(visitor: Type.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class FunctionType(
    val staticParameters: List<StaticParameter>,
    val positionalParameters: List<Type>,
    val namedParameters: Map<Identifier, Type>,
    val returns: Type,
    val effect: Effect
): Type {
    override val shapeId: Int?
        get() = null

    override fun fieldType(fieldName: Identifier): Type? = null

    override fun replaceStaticValues(bindings: StaticBindings): Type {
        return FunctionType(
            positionalParameters = positionalParameters.map({ parameter -> replaceStaticValuesInType(parameter, bindings) }),
            namedParameters = namedParameters.mapValues({ parameter -> replaceStaticValuesInType(parameter.value, bindings) }),
            effect = replaceEffects(effect, bindings),
            returns = replaceStaticValuesInType(returns, bindings),
            staticParameters = staticParameters
        )
    }

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

    override fun <T> accept(visitor: Type.Visitor<T>): T {
        return visitor.visit(this)
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
    override val shapeId: Int?
        get() = null

    override val shortDescription: String
        get() = "#(${elementTypes.map(Type::shortDescription).joinToString(", ")})"

    override fun fieldType(fieldName: Identifier): Type? {
        return null
    }

    override fun replaceStaticValues(bindings: StaticBindings): Type {
        return TupleType(
            elementTypes = elementTypes.map { elementType ->
                replaceStaticValuesInType(elementType, bindings)
            }
        )
    }

    override fun <T> accept(visitor: Type.Visitor<T>): T {
        return visitor.visit(this)
    }
}

interface TypeAlias: Type {
    val name: Identifier
    val aliasedType: Type

    override val shapeId: Int?
        get() = aliasedType.shapeId

    override fun fieldType(fieldName: Identifier): Type? {
        return aliasedType.fieldType(fieldName)
    }

    override fun replaceStaticValues(bindings: StaticBindings): Type {
        // TODO: test this
        return aliasedType.replaceStaticValues(bindings)
    }

    override fun <T> accept(visitor: Type.Visitor<T>): T {
        return visitor.visit(this)
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
    override val shapeId: Int
    val tagValue: TagValue?
    val allFields: Map<Identifier, Field>
    val populatedFieldNames: Set<Identifier>
    val staticParameters: List<StaticParameter>
    val staticArguments: List<StaticValue>

    val populatedFields: Map<Identifier, Field>
        get() = allFields.filterKeys { fieldName -> populatedFieldNames.contains(fieldName) }

    override val shortDescription: String
        get() {
            val base = if (staticArguments.isEmpty()) {
                name.value
            } else {
                appliedTypeShortDescription(name, staticArguments)
            }

            if (populatedFieldNames == allFields.keys) {
                return base
            } else {
                val populatedFieldNamesString = populatedFieldNames.joinToString("") { fieldName -> ", ." + fieldName.value }
                return "Partial[$base$populatedFieldNamesString]"
            }
        }

    override fun fieldType(fieldName: Identifier): Type? {
        // TODO: test this
        if (fieldName in populatedFieldNames) {
            return allFields[fieldName]?.type
        } else {
            return null
        }
    }

    override fun replaceStaticValues(bindings: StaticBindings): Type {
        return LazyShapeType(
            name = name,
            getAllFields = lazy {
                allFields.mapValues { field -> replaceStaticValuesInField(field.value, bindings) }
            },
            getPopulatedFieldNames = { populatedFieldNames },
            tagValue = tagValue,
            shapeId = shapeId,
            staticParameters = staticParameters,
            staticArguments = staticArguments.map { argument -> replaceStaticValues(argument, bindings) }
        )
    }

    override fun <T> accept(visitor: Type.Visitor<T>): T {
        return visitor.visit(this)
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

fun lazyCompleteShapeType(
    shapeId: Int,
    name: Identifier,
    tagValue: TagValue?,
    getFields: Lazy<List<Field>>,
    staticParameters: List<StaticParameter>,
    staticArguments: List<StaticValue>
) = lazyShapeType(
    shapeId = shapeId,
    name = name,
    getAllFields = getFields,
    getPopulatedFieldNames = lazy {
        getFields.value.map { field -> field.name }.toSet()
    },
    tagValue = tagValue,
    staticParameters = staticParameters,
    staticArguments = staticArguments
)

fun lazyShapeType(
    shapeId: Int,
    name: Identifier,
    tagValue: TagValue?,
    getAllFields: Lazy<List<Field>>,
    getPopulatedFieldNames: Lazy<Set<Identifier>>,
    staticParameters: List<StaticParameter>,
    staticArguments: List<StaticValue>
) = LazyShapeType(
    shapeId = shapeId,
    name = name,
    getAllFields = lazy {
        getAllFields.value.associateBy { field -> field.name }
    },
    getPopulatedFieldNames = { getPopulatedFieldNames.value },
    tagValue = tagValue,
    staticParameters = staticParameters,
    staticArguments = staticArguments
)

class LazyShapeType(
    override val name: Identifier,
    getAllFields: Lazy<Map<Identifier, Field>>,
    private val getPopulatedFieldNames: () -> Set<Identifier>,
    override val shapeId: Int = freshTypeId(),
    override val tagValue: TagValue?,
    override val staticParameters: List<StaticParameter>,
    override val staticArguments: List<StaticValue>
): ShapeType {
    override val allFields: Map<Identifier, Field> by getAllFields
    override val populatedFieldNames: Set<Identifier>
        get() = getPopulatedFieldNames()
}

fun updatedType(baseType: Type, shapeType: ShapeType, field: Field): Type {
    if (baseType.shapeId == null) {
        throw CompilerError("cannot update non-shape type", source = NullSource)
    } else if (baseType.shapeId != field.shapeId) {
        throw CompilerError("base type and field are different shapes", source = NullSource)
    } else if (!shapeType.populatedFields.containsValue(field)) {
        throw CompilerError("field does not belong to shape", source = NullSource)
    } else if (baseType is ShapeType) {
        return createPartialShapeType(baseType, populatedFieldNames = baseType.populatedFieldNames + setOf(field.name))
    } else {
        return UpdatedType(baseType = baseType, shapeType = shapeType, field = field)
    }
}

class UpdatedType internal constructor(
    val baseType: Type,
    val shapeType: ShapeType,
    val field: Field
): Type {
    override val shapeId: Int
        get() = shapeType.shapeId

    override val shortDescription: String
        get() = "${baseType.shortDescription} + @(${shapeType.shortDescription}.fields.${this.field.name.value}: ${this.field.type.shortDescription})"

    override fun fieldType(fieldName: Identifier): Type? {
        // TODO: implement field type
        throw UnsupportedOperationException("not implemented")
    }

    override fun replaceStaticValues(bindings: StaticBindings): Type {
        val newShapeType = replaceStaticValuesInType(shapeType, bindings) as ShapeType
        return updatedType(
            baseType = replaceStaticValuesInType(baseType, bindings),
            shapeType = newShapeType,
            field = newShapeType.allFields[field.name]!!,
        )
    }

    override fun <T> accept(visitor: Type.Visitor<T>): T {
        return visitor.visit(this)
    }
}

interface UnionType: Type {
    val name: Identifier
    val tag: Tag
    val members: List<Type>
    val staticArguments: List<StaticValue>

    override fun replaceStaticValues(bindings: StaticBindings): Type {
        return LazyUnionType(
            tag,
            name,
            lazy {
                members.map { memberType -> replaceStaticValuesInType(memberType, bindings) as ShapeType }
            },
            staticArguments = staticArguments.map { argument -> replaceStaticValues(argument, bindings) }
        )
    }

    override val shapeId: Int?
        get() = null

    override fun fieldType(fieldName: Identifier): Type? = null

    override fun <T> accept(visitor: Type.Visitor<T>): T {
        return visitor.visit(this)
    }
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

// TODO: Remove default for source
fun union(left: Type, right: Type, source: Source = NullSource): Type {
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
        val members = (leftMembers + rightMembers).distinct().map { member ->
            if (member is ShapeType) {
                member
            } else {
                throw CannotUnionTypesError(left, right, source = source)
            }
        }

        val tags = members.map { member ->
            val tagValue = member.tagValue
            if (tagValue == null) {
                throw CannotUnionTypesError(left, right, source = source)
            } else {
                tagValue.tag
            }
        }.distinct()

        val tag = if (tags.size == 1) {
            tags.single()
        } else {
            throw CannotUnionTypesError(left, right, source = source)
        }

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

    override val shapeId: Int?
        get() = null

    override fun fieldType(fieldName: Identifier): Type? {
        return null
    }

    override fun replaceStaticValues(bindings: StaticBindings): Type {
        throw UnsupportedOperationException("not implemented")
    }

    override fun <T> accept(visitor: Type.Visitor<T>): T {
        return visitor.visit(this)
    }
}

fun invariantTypeParameter(name: String, shapeId: Int? = null, source: Source = NullSource) = TypeParameter(
    Identifier(name),
    variance = Variance.INVARIANT,
    shapeId = shapeId,
    source = source,
)

fun covariantTypeParameter(name: String, source: Source = NullSource) = TypeParameter(
    Identifier(name),
    variance = Variance.COVARIANT,
    shapeId = null,
    source = source,
)

fun contravariantTypeParameter(name: String, source: Source = NullSource) = TypeParameter(
    Identifier(name),
    variance = Variance.CONTRAVARIANT,
    shapeId = null,
    source = source,
)

fun effectParameter(name: String, source: Source = NullSource) = EffectParameter(Identifier(name), source = source)

object CastType : BasicType {
    override val shortDescription = "Cast"
}

object EmptyFunctionType : BasicType {
    override val shortDescription = "Empty"
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
    return value.acceptStaticValueVisitor(object : StaticValue.Visitor<ValidateTypeResult> {
        override fun visit(effect: Effect): ValidateTypeResult {
            return ValidateTypeResult.success
        }

        override fun visit(value: ParameterizedStaticValue): ValidateTypeResult {
            return validateStaticValue(value.value)
        }

        override fun visit(type: Type): ValidateTypeResult {
            return validateType(type)
        }

        override fun visit(type: EmptyTypeFunction): ValidateTypeResult {
            return ValidateTypeResult.success
        }

    })
}

fun validateType(type: Type): ValidateTypeResult {
    return type.accept(object : Type.Visitor<ValidateTypeResult> {
        override fun visit(type: BasicType): ValidateTypeResult {
            return ValidateTypeResult.success
        }

        override fun visit(type: FunctionType): ValidateTypeResult {
            if (type.returns is TypeParameter && type.returns.variance == Variance.CONTRAVARIANT) {
                return ValidateTypeResult(listOf("return type cannot be contravariant"))
            } else {
                val parameterTypes = type.positionalParameters + type.namedParameters.values
                return ValidateTypeResult(parameterTypes.mapNotNull { parameterType ->
                    if (parameterType is TypeParameter && parameterType.variance == Variance.COVARIANT) {
                        "parameter type cannot be covariant"
                    } else {
                        null
                    }
                })
            }
        }

        override fun visit(type: ModuleType): ValidateTypeResult {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(type: ShapeType): ValidateTypeResult {
            return ValidateTypeResult(type.allFields.mapNotNull { field ->
                val fieldType = field.value.type
                if (fieldType is TypeParameter && fieldType.variance == Variance.CONTRAVARIANT) {
                    "field type cannot be contravariant"
                } else {
                    null
                }
            })
        }

        override fun visit(type: StaticValueType): ValidateTypeResult {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(type: TupleType): ValidateTypeResult {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(type: TypeAlias): ValidateTypeResult {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(type: TypeParameter): ValidateTypeResult {
            return ValidateTypeResult.success
        }

        override fun visit(type: UnionType): ValidateTypeResult {
            return ValidateTypeResult.success
        }

        override fun visit(type: UpdatedType): ValidateTypeResult {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(type: VarargsType): ValidateTypeResult {
            throw UnsupportedOperationException("not implemented")
        }

    })
}

fun applyStatic(
    receiver: ParameterizedStaticValue,
    arguments: List<StaticValue>,
    source: Source = NullSource
): StaticValue {
    if (receiver.parameters.size != arguments.size) {
        throw CompilerError(
            "parameter count (${receiver.parameters.size}) != argument count (${arguments.size})",
            source = source
        )
    }

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

        override fun visit(type: EmptyTypeFunction): StaticValue {
            return type
        }
    })
}

fun replaceStaticValuesInType(type: Type, bindings: StaticBindings): Type {
    if (bindings.isEmpty()) {
        return type
    } else {
        return type.replaceStaticValues(bindings)
    }
}

private fun replaceStaticValuesInField(field: Field, bindings: StaticBindings) =
    field.mapType { type ->
        replaceStaticValuesInType(type, bindings)
    }


fun replaceEffects(effect: Effect, bindings: StaticBindings): Effect {
    when (effect) {
        is EffectParameter ->
            // TODO: handle non-effect bindings
            return bindings.getOrElse(effect, { effect }) as Effect

        is EffectUnion ->
            return effectUnion(effect.members.map { member -> replaceEffects(member, bindings) })

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
