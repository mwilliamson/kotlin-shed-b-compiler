package org.shedlang.compiler.types

import org.shedlang.compiler.CannotUnionTypesError
import org.shedlang.compiler.InternalCompilerError
import org.shedlang.compiler.ast.*

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

object TypeLevelValueTypeGroup: TypeGroup {
    override val shortDescription: String
        get() = "type-level value"
}

interface Type: TypeLevelValue, TypeGroup {
    val shapeId: Int?

    /**
     * The fields of the type. If null, no assumptions can be made about the
     * fields present on values of this type.
     */
    val fields: Map<Identifier, Field>?

    fun replaceValues(bindings: TypeLevelBindings): Type

    override fun <T> accept(visitor: TypeLevelValue.Visitor<T>): T {
        return visitor.visit(this)
    }

    fun <T> accept(visitor: Visitor<T>): T

    interface Visitor<T> {
        fun visit(type: BasicType): T
        fun visit(type: FunctionType): T
        fun visit(type: ModuleType): T
        fun visit(type: ShapeType): T
        fun visit(type: TypeLevelValueType): T
        fun visit(type: TupleType): T
        fun visit(type: TypeAlias): T
        fun visit(type: TypeParameter): T
        fun visit(type: UnionType): T
        fun visit(type: VarargsType): T
    }
}

interface BasicType : Type {
    override val shapeId: Int?
        get() = null

    override val fields: Map<Identifier, Field>?
        get() = null

    override fun replaceValues(bindings: TypeLevelBindings): Type {
        return this
    }

    override fun <T> accept(visitor: Type.Visitor<T>): T {
        return visitor.visit(this)
    }
}

object UnitType: BasicType {
    override val shortDescription = "Unit"
}

val UnitMetaType = metaType(UnitType)

object BoolType : BasicType {
    override val shortDescription = "Bool"
}

val BoolMetaType = metaType(BoolType)

object IntType : BasicType{
    override val shortDescription = "Int"
}

val IntMetaType = metaType(IntType)

object UnicodeScalarType : BasicType {
    override val shortDescription = "UnicodeScalar"
}

val UnicodeScalarMetaType = metaType(UnicodeScalarType)

object StringType : BasicType {
    override val shortDescription = "String"
}

val StringMetaType = metaType(StringType)

object StringSliceType : BasicType {
    override val shortDescription = "StringSlice"
}

val StringSliceMetaType = metaType(StringSliceType)

object AnyType : BasicType {
    override val shortDescription = "Any"
}

val AnyMetaType = metaType(AnyType)

object NothingType : BasicType {
    override val shortDescription = "Nothing"
}

val NothingMetaType = metaType(NothingType)

data class TypeLevelValueType(val value: TypeLevelValue): Type {
    override val shapeId: Int?
        get() = null

    override val shortDescription: String
        get() = "TypeLevelValue[${value.shortDescription}]"

    override val fields: Map<Identifier, Field>?
        get() {
            val rawValue = rawValue(value)
            if (value is UserDefinedEffect) {
                return value.operations.map { (operationName, operationType) ->
                    Field(
                        name = operationName,
                        shapeId = value.definitionId,
                        type = operationType,
                    )
                }.associateBy { field -> field.name }
            } else if (rawValue is Type) {
                // TODO: better handling of generics
                // TODO: test this for constructed types
                val shapeId = freshTypeId()

                val fields = listOf(
                    Field(
                        name = Identifier("fields"),
                        shapeId = shapeId,
                        type = when (rawValue) {
                            is ShapeType ->
                                shapeFieldsInfoType(rawValue)
                            else ->
                                UnitType
                        },
                    ),
                    Field(
                        // TODO: Restrict to shape types?
                        name = Identifier("name"),
                        shapeId = shapeId,
                        type = StringType,
                    )
                )

                return fields.associateBy { field -> field.name }
            } else {
                return null
            }
        }

    override fun replaceValues(bindings: TypeLevelBindings): Type {
        return TypeLevelValueType(replaceTypeLevelValues(value, bindings))
    }

    override fun <T> accept(visitor: Type.Visitor<T>): T {
        return visitor.visit(this)
    }
}

fun effectType(effect: Effect) = TypeLevelValueType(effect)
fun metaType(type: Type) = TypeLevelValueType(type)

private fun shapeFieldsInfoType(shapeType: ShapeType): Type {
    val shapeId = freshTypeId()
    val fields = shapeType.fields.values.map { field ->
        Field(
            shapeId = shapeId,
            name = field.name,
            type = shapeFieldInfoType(shapeType, field),
        )
    }

    return lazyShapeType(
        shapeId = shapeId,
        qualifiedName = shapeType.qualifiedName.addTypeName("Fields"),
        tagValue = null,
        getFields = lazy {
            fields
        },
    )
}

val shapeFieldTypeFunctionTypeParameter = contravariantTypeParameter("Type")
val shapeFieldTypeFunctionFieldParameter = covariantTypeParameter("Field")
val shapeFieldTypeFunctionParameters = listOf(
    shapeFieldTypeFunctionTypeParameter,
    shapeFieldTypeFunctionFieldParameter,
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
    ),
    Field(
        shapeId = shapeFieldTypeFunctionShapeId,
        name = Identifier("name"),
        type = StringType,
    ),
    Field(
        shapeId = shapeFieldTypeFunctionShapeId,
        name = Identifier("update"),
        type = functionType(
            positionalParameters = listOf(shapeFieldTypeFunctionFieldParameter, shapeFieldTypeFunctionTypeParameter),
            returns = shapeFieldTypeFunctionTypeParameter
        ),
    ),
)
val ShapeFieldTypeFunction = TypeConstructor(
    parameters = shapeFieldTypeFunctionParameters,
    genericType = lazyShapeType(
        shapeId = shapeFieldTypeFunctionShapeId,
        qualifiedName = QualifiedName.builtin("ShapeField"),
        tagValue = null,
        getFields = lazy {
            shapeFieldTypeFunctionFields
        },
    )
)

private fun shapeFieldInfoType(shapeType: Type, field: Field): Type {
    return applyTypeLevel(
        ShapeFieldTypeFunction,
        listOf(shapeType, field.type),
    )
}

fun metaTypeToType(type: Type): Type? {
    if (type is TypeLevelValueType) {
        return type.value as? Type
    } else {
        return null
    }
}

fun rawValue(value: TypeLevelValue): TypeLevelValue {
    return when (value) {
        is TypeConstructor -> value.genericType
        else -> value
    }
}

fun stripGenerics(type: Type): Type {
    return when (type) {
        is ConstructedType2 -> type.constructor.genericType
        else -> type
    }
}

private var nextEffectParameterId = 0
fun freshEffectParameterId() = nextEffectParameterId++

fun freshTypeId() = freshNodeId()

interface TypeLevelParameter: TypeLevelValue {
    val name: Identifier
    val source: Source

    fun fresh(): TypeLevelParameter

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
): TypeLevelParameter, Type {
    override val fields: Map<Identifier, Field>?
        get() = null

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

    override fun replaceValues(bindings: TypeLevelBindings): Type {
        return bindings.getOrElse(this, { this }) as Type
    }

    override fun <T> accept(visitor: TypeLevelParameter.Visitor<T>): T {
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

    override fun toString(): String {
        return shortDescription
    }
}

data class EffectParameter(
    override val name: Identifier,
    val typeLevelParameterId: Int = freshEffectParameterId(),
    override val source: Source,
): TypeLevelParameter, Effect {
    override val shortDescription: String
        get() = name.value

    override fun <T> accept(visitor: TypeLevelParameter.Visitor<T>): T {
        return visitor.visit(this)
    }

    override fun fresh(): EffectParameter {
        return EffectParameter(
            name = name,
            source = source,
        )
    }

    override fun toString(): String {
        return shortDescription
    }
}

enum class Variance {
    INVARIANT,
    COVARIANT,
    CONTRAVARIANT
}

data class TypeConstructor(
    val parameters: List<TypeLevelParameter>,
    val genericType: Type
): TypeLevelValue {
    override val shortDescription: String
    // TODO: should be something like (T, U) => Shape[T, U]
        get() = "TypeFunction(TODO)"

    override fun <T> accept(visitor: TypeLevelValue.Visitor<T>): T {
        return visitor.visit(this)
    }
}

object CastableTypeLevelFunction: TypeLevelValue {
    override val shortDescription: String
        get() = "Castable"

    override fun <T> accept(visitor: TypeLevelValue.Visitor<T>): T {
        return visitor.visit(this)
    }
}

class CastableType(val type: Type): Type {
    override val shapeId: Int?
        get() = null

    override val shortDescription: String
        get() = "Castable[${type.shortDescription}]"

    override val fields: Map<Identifier, Field>?
        get() = null

    override fun replaceValues(bindings: TypeLevelBindings): Type {
        return CastableType(replaceTypeLevelValuesInType(type, bindings))
    }

    override fun <T> accept(visitor: Type.Visitor<T>): T {
        throw UnsupportedOperationException("not implemented")
    }
}

fun castableType(type: Type) = CastableType(type)

object MetaTypeTypeLevelFunction: TypeLevelValue {
    override val shortDescription: String
        get() = "Type"

    override fun <T> accept(visitor: TypeLevelValue.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class ModuleType(
    val name: ModuleName,
    override val fields: Map<Identifier, Field>
): Type {
    override val shapeId: Int?
        get() = null

    override fun replaceValues(bindings: TypeLevelBindings): Type {
        throw UnsupportedOperationException("not implemented")
    }

    override val shortDescription: String
        get() = "module ${formatModuleName(name)}"

    override fun <T> accept(visitor: Type.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class FunctionType(
    val typeLevelParameters: List<TypeLevelParameter>,
    val positionalParameters: List<Type>,
    val namedParameters: Map<Identifier, Type>,
    val returns: Type,
    val effect: Effect
): Type {
    override val shapeId: Int?
        get() = null

    override val fields: Map<Identifier, Field>?
        get() = null

    override fun replaceValues(bindings: TypeLevelBindings): Type {
        return FunctionType(
            positionalParameters = positionalParameters.map({ parameter -> replaceTypeLevelValuesInType(parameter, bindings) }),
            namedParameters = namedParameters.mapValues({ parameter -> replaceTypeLevelValuesInType(parameter.value, bindings) }),
            effect = replaceEffects(effect, bindings),
            returns = replaceTypeLevelValuesInType(returns, bindings),
            typeLevelParameters = typeLevelParameters
        )
    }

    override val shortDescription: String
        get() {
            val typeParameters = typeLevelArgumentsString(typeLevelParameters)

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

private fun typeLevelArgumentsString(values: List<TypeLevelValue>): String {
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

    override val fields: Map<Identifier, Field>?
        get() = null

    override fun replaceValues(bindings: TypeLevelBindings): Type {
        return TupleType(
            elementTypes = elementTypes.map { elementType ->
                replaceTypeLevelValuesInType(elementType, bindings)
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

    override val fields: Map<Identifier, Field>?
        get() = aliasedType.fields

    override fun replaceValues(bindings: TypeLevelBindings): Type {
        // TODO: test this
        return aliasedType.replaceValues(bindings)
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

interface ConstructedType2 : Type {
    val constructor: TypeConstructor
    val args: List<TypeLevelValue>
}

data class Tag(val qualifiedName: QualifiedName)
data class TagValue(val tag: Tag, val value: Identifier)

interface ShapeType : Type {
    val qualifiedName: QualifiedName

    val name: Identifier
        get() = qualifiedName.shortName

    override val shapeId: Int
    val tagValue: TagValue?
    override val fields: Map<Identifier, Field>
}

interface SimpleShapeType : ShapeType {
    override val shortDescription: String
        get() = name.value

    override fun replaceValues(bindings: TypeLevelBindings): Type {
        return this
    }

    override fun <T> accept(visitor: Type.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class ConstructedShapeType(
    override val constructor: TypeConstructor,
    private val genericType: SimpleShapeType,
    override val args: List<TypeLevelValue>,
): ConstructedType2, ShapeType {

    private val bindings = constructor.parameters.zip(args).toMap()

    override val qualifiedName: QualifiedName
        // TODO: should stick on type args
        get() = genericType.qualifiedName

    override val tagValue: TagValue?
        get() = genericType.tagValue

    override val fields: Map<Identifier, Field> by lazy {
        replaceTypeLevelValuesInFields(genericType.fields, bindings)
    }

    override fun replaceValues(bindings: TypeLevelBindings): Type {
        // TODO: test this
        return copy(args = args.map { arg ->
            replaceTypeLevelValues(arg, bindings)
        })
    }

    override fun <T> accept(visitor: Type.Visitor<T>): T {
        return visitor.visit(this);
    }

    override val shortDescription: String
        get() = appliedTypeShortDescription(constructor.genericType.shortDescription, args)

    override val shapeId: Int
        get() = genericType.shapeId

    override fun toString(): String {
        return shortDescription
    }
}

data class Field(
    val shapeId: Int,
    val name: Identifier,
    val type: Type,
) {
    fun mapType(func: (Type) -> Type): Field = Field(
        shapeId = shapeId,
        name = name,
        type = func(type),
    )
}

fun lazyShapeType(
    shapeId: Int,
    qualifiedName: QualifiedName,
    tagValue: TagValue?,
    getFields: Lazy<List<Field>>,
) = LazySimpleShapeType(
    shapeId = shapeId,
    qualifiedName = qualifiedName,
    getAllFields = lazy {
        getFields.value.associateBy { field -> field.name }
    },
    tagValue = tagValue,
)

class LazySimpleShapeType(
    override val qualifiedName: QualifiedName,
    getAllFields: Lazy<Map<Identifier, Field>>,
    override val shapeId: Int = freshTypeId(),
    override val tagValue: TagValue?,
): SimpleShapeType {
    override val fields: Map<Identifier, Field> by getAllFields
}

interface UnionType : Type {
    val tag: Tag
    val members: List<Type>

    override val shapeId: Int?
        get() = null

    override val fields: Map<Identifier, Field>?
        get() = null

    override fun <T> accept(visitor: Type.Visitor<T>): T {
        return visitor.visit(this)
    }
}

interface SimpleUnionType: UnionType {
    val name: Identifier

    override fun replaceValues(bindings: TypeLevelBindings): Type {
        return LazySimpleUnionType(
            tag,
            name,
            lazy {
                members.map { memberType -> replaceTypeLevelValuesInType(memberType, bindings) as SimpleShapeType }
            },
        )
    }
}

data class ConstructedUnionType(
    override val constructor: TypeConstructor,
    private val genericType: SimpleUnionType,
    override val args: List<TypeLevelValue>,
) : ConstructedType2, UnionType {
    private val bindings = constructor.parameters.zip(args).toMap()

    override val tag: Tag
        get() = genericType.tag

    override val members: List<Type> by lazy {
        genericType.members.map { member ->
            // TODO: simplify this
            when (member) {
                is SimpleShapeType ->
                    member
                is ConstructedShapeType ->
                    replaceTypeLevelValuesInType(member, bindings)
                else ->
                    throw InternalCompilerError("unexpected union member", NullSource)
            }
        }
    }

    override fun replaceValues(bindings: TypeLevelBindings): Type {
        // TODO: test this
        return copy(args = args.map { arg ->
            replaceTypeLevelValues(arg, bindings)
        })
    }

    override val shortDescription: String
        get() = appliedTypeShortDescription(constructor.genericType.shortDescription, args)
}

data class AnonymousUnionType(
    override val tag: Tag,
    override val name: Identifier = Identifier("_Union" + freshTypeId()),
    override val members: List<Type>
): SimpleUnionType {
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
                is SimpleUnionType -> type.members
                else -> listOf(type)
            }
        }

        val leftMembers = findMembers(left)
        val rightMembers = findMembers(right)
        val members = (leftMembers + rightMembers).distinct().map { member ->
            if (stripGenerics(member) is SimpleShapeType) {
                member
            } else {
                throw CannotUnionTypesError(left, right, source = source)
            }
        }

        val tags = members.map { member ->
            val tagValue = (stripGenerics(member) as SimpleShapeType).tagValue
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

data class LazySimpleUnionType(
    override val tag: Tag,
    override val name: Identifier,
    private val getMembers: Lazy<List<Type>>,
): SimpleUnionType {
    override val shortDescription: String
        get() = name.value

    override val members: List<Type> by getMembers
}

fun functionType(
    typeLevelParameters: List<TypeLevelParameter> = listOf(),
    positionalParameters: List<Type> = listOf(),
    namedParameters: Map<Identifier, Type> = mapOf(),
    returns: Type = UnitType,
    effect: Effect = EmptyEffect
) = FunctionType(
    typeLevelParameters = typeLevelParameters,
    positionalParameters = positionalParameters,
    namedParameters = namedParameters,
    returns = returns,
    effect = effect
)

fun positionalFunctionType(parameters: List<Type>, returns: Type)
    = functionType(positionalParameters = parameters, returns = returns)

data class VarargsType(val qualifiedName: QualifiedName, val cons: FunctionType, val nil: Type): Type {
    override val shortDescription: String
        get() = "varargs ${qualifiedName.shortName.value}(${cons.shortDescription}, ${nil.shortDescription})"

    override val shapeId: Int?
        get() = null

    override val fields: Map<Identifier, Field>?
        get() = null

    override fun replaceValues(bindings: TypeLevelBindings): Type {
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

private fun appliedTypeShortDescription(name: String, parameters: List<TypeLevelValue>): String {
    val parametersString = parameters.joinToString(separator = ", ", transform = { type -> type.shortDescription })
    return "$name[$parametersString]"
}

data class ValidateTypeResult(val errors: List<String>) {
    companion object {
        val success = ValidateTypeResult(listOf())
    }
}

fun validateTypeLevelValue(value: TypeLevelValue): ValidateTypeResult {
    return value.accept(object : TypeLevelValue.Visitor<ValidateTypeResult> {
        override fun visit(effect: Effect): ValidateTypeResult {
            return ValidateTypeResult.success
        }

        override fun visit(value: TypeConstructor): ValidateTypeResult {
            return validateTypeLevelValue(value.genericType)
        }

        override fun visit(type: Type): ValidateTypeResult {
            return validateType(type)
        }

        override fun visit(value: CastableTypeLevelFunction): ValidateTypeResult {
            return ValidateTypeResult.success
        }

        override fun visit(value: MetaTypeTypeLevelFunction): ValidateTypeResult {
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
            return ValidateTypeResult(type.fields.mapNotNull { field ->
                val fieldType = field.value.type
                if (fieldType is TypeParameter && fieldType.variance == Variance.CONTRAVARIANT) {
                    "field type cannot be contravariant"
                } else {
                    null
                }
            })
        }

        override fun visit(type: TypeLevelValueType): ValidateTypeResult {
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

        override fun visit(type: VarargsType): ValidateTypeResult {
            throw UnsupportedOperationException("not implemented")
        }

    })
}

fun applyTypeLevel(
    constructor: TypeConstructor,
    arguments: List<TypeLevelValue>,
    source: Source = NullSource
): ConstructedType2 {
    if (constructor.parameters.size != arguments.size) {
        throw InternalCompilerError(
            "parameter count (${constructor.parameters.size}) != argument count (${arguments.size})",
            source = source
        )
    }

    if (constructor.genericType is SimpleShapeType) {
        return ConstructedShapeType(
            constructor = constructor,
            genericType = constructor.genericType,
            args = arguments,
        )
    } else if (constructor.genericType is SimpleUnionType) {
        return ConstructedUnionType(
            constructor = constructor,
            genericType = constructor.genericType,
            args = arguments,
        )
    } else {
        // TODO: rearrange types to make this impossible?
        throw InternalCompilerError("unexpected type constructor generic type", NullSource)
    }
}

typealias TypeLevelBindings = Map<TypeLevelParameter, TypeLevelValue>

private fun replaceTypeLevelValues(value: TypeLevelValue, bindings: TypeLevelBindings): TypeLevelValue {
    return value.accept(object : TypeLevelValue.Visitor<TypeLevelValue> {
        override fun visit(effect: Effect): TypeLevelValue {
            return replaceEffects(effect, bindings)
        }

        override fun visit(value: TypeConstructor): TypeLevelValue {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(type: Type): TypeLevelValue {
            return replaceTypeLevelValuesInType(type, bindings)
        }

        override fun visit(value: CastableTypeLevelFunction): TypeLevelValue {
            return value
        }

        override fun visit(value: MetaTypeTypeLevelFunction): TypeLevelValue {
            return value
        }
    })
}

fun replaceTypeLevelValuesInType(type: Type, bindings: TypeLevelBindings): Type {
    if (bindings.isEmpty()) {
        return type
    } else {
        return type.replaceValues(bindings)
    }
}

private fun replaceTypeLevelValuesInFields(fields: Map<Identifier, Field>, bindings: TypeLevelBindings) =
    fields.mapValues { field -> replaceTypeLevelValuesInField(field.value, bindings) }

private fun replaceTypeLevelValuesInField(field: Field, bindings: TypeLevelBindings) =
    field.mapType { type ->
        replaceTypeLevelValuesInType(type, bindings)
    }


fun replaceEffects(effect: Effect, bindings: TypeLevelBindings): Effect {
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

fun findDiscriminator(sourceType: Type, targetType: TypeLevelValue): Discriminator? {
    // TODO: test handling of sourceType being a ConstructedType

    if (sourceType !is UnionType) {
        return null
    }

    val tagValue = tagValue(targetType)
    if (tagValue?.tag != sourceType.tag) {
        return null
    }

    val matchingMembers = sourceType.members.filter { member ->
        tagValue(member) == tagValue
    }
    val refinedType = unionAll(matchingMembers)

    when (targetType) {
        is Type -> {
            if (!canCoerce(from = refinedType, to = targetType)) {
                return null
            }
        }
        is TypeConstructor -> {
            // TODO: test this
            if (!canCoerce(from = refinedType, to = applyTypeLevel(targetType, targetType.parameters), freeParameters = targetType.parameters.toSet())) {
                return null
            }
        }
        else -> {
            return null
        }
    }

    return Discriminator(tagValue = tagValue, targetType = refinedType)
}

private fun tagValue(targetType: TypeLevelValue): TagValue? {
    return when (targetType) {
        is ShapeType ->
            targetType.tagValue
        is TypeConstructor ->
            tagValue(targetType.genericType)
        else ->
            null
    }
}
