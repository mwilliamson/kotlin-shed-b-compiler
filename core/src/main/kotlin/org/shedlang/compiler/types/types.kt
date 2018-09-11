package org.shedlang.compiler.types

import org.shedlang.compiler.Types
import org.shedlang.compiler.ast.*


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
data class SymbolType(val symbol: Symbol): BasicType {
    override val shortDescription: String
        get() = "Symbol[${symbol.module}.${symbol.name}]"
}

data class Symbol(val module: List<Identifier>, val name: String)

object AnySymbolType : Type {
    override val shortDescription: String
        get() = "Symbol"
}
object AnyType : Type {
    override val shortDescription = "Any"
}
object NothingType : Type {
    override val shortDescription = "Nothing"
}

val metaTypeParameter = covariantTypeParameter("T")
val metaTypeShapeId = freshShapeId()
val metaType = TypeFunction(
    parameters = listOf(metaTypeParameter),
    type = LazyShapeType(
        shapeId = metaTypeShapeId,
        name = Identifier("Type"),
        staticParameters = listOf(metaTypeParameter),
        staticArguments = listOf(metaTypeParameter),
        getFields = lazy({ mapOf<Identifier, Field>() })
    )
)

fun MetaType(type: Type): Type {
    return applyStatic(metaType, listOf(type))
}

fun metaTypeToType(type: Type): Type? {
    if (type is ShapeType && type.shapeId == metaTypeShapeId) {
        val argument = type.staticArguments[0]
        if (argument is Type) {
            return argument
        } else {
            // TODO: throw a better exception
            throw Exception("expected type")
        }
    } else {
        return null
    }
}

fun rawType(type: Type): Type {
    return when (type) {
        is TypeFunction -> type.type
        else -> type
    }
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

fun freshShapeId() = freshNodeId()

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

data class ModuleType(
    val fields: Map<Identifier, Type>
): Type {
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

interface ShapeType: Type {
    val name: Identifier
    val shapeId: Int
    val fields: Map<Identifier, Field>
    val staticParameters: List<StaticParameter>
    val staticArguments: List<StaticValue>
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
    getFields: Lazy<List<Field>>,
    staticParameters: List<StaticParameter>,
    staticArguments: List<StaticValue>
) = LazyShapeType(
    shapeId = shapeId,
    name = name,
    getFields = lazy {
        getFields.value.associateBy { field -> field.name }
    },
    staticParameters = staticParameters,
    staticArguments = staticArguments
)

data class LazyShapeType(
    override val name: Identifier,
    private val getFields: Lazy<Map<Identifier, Field>>,
    override val shapeId: Int = freshShapeId(),
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
    val members: List<Type>
    val staticArguments: List<StaticValue>
}


data class AnonymousUnionType(
    override val name: Identifier = Identifier("_Union" + freshAnonymousTypeId()),
    override val members: List<Type>
): UnionType {
    override val staticArguments: List<StaticValue>
        get() = listOf()

    override val shortDescription: String
        get() = name.value
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

        return AnonymousUnionType(members = (leftMembers + rightMembers).distinct())
    }
}

data class LazyUnionType(
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
        getFields = lazy({ mapOf<Identifier, Field>() })
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
            val fieldType = field.value.type
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

fun applyStatic(receiver: TypeFunction, arguments: List<StaticValue>): Type {
    val bindings = receiver.parameters.zip(arguments).toMap()
    return replaceStaticValuesInType(receiver.type, bindings = bindings)
}

typealias StaticBindings = Map<StaticParameter, StaticValue>

private fun replaceStaticValues(value: StaticValue, bindings: StaticBindings): StaticValue {
    return value.acceptStaticValueVisitor(object : StaticValue.Visitor<StaticValue> {
        override fun visit(effect: Effect): StaticValue {
            return replaceEffects(effect, bindings)
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
    } else if (type is UnitType || type is BoolType || type is IntType || type is StringType || type is CharType || type is AnyType || type is SymbolType) {
        return type
    } else {
        throw NotImplementedError("Type replacement not implemented for: " + type)
    }
}

public fun replaceEffects(effect: Effect, bindings: Map<StaticParameter, StaticValue>): Effect {
    val effectParameter = effect as? EffectParameter
    if (effectParameter == null) {
        return effect
    } else {
        // TODO: handle non-effect bindings
        return bindings.getOrElse(effect, { effect }) as Effect
    }
}

public data class Discriminator(val field: Field, val symbolType: SymbolType) {
    val fieldName: Identifier = field.name
}

fun findDiscriminator(node: IsNode, types: Types): Discriminator {
    return findDiscriminator(node.expression, node.type, types = types)
}

fun findDiscriminator(node: WhenNode, branch: WhenBranchNode, types: Types): Discriminator {
    val expression = node.expression
    val type = branch.type
    return findDiscriminator(expression, type, types = types)
}

fun findDiscriminator(expression: ExpressionNode, type: StaticNode, types: Types): Discriminator {
    val sourceType = types.typeOf(expression)
    val targetType = types.rawTypeValue(type) as ShapeType
    return findDiscriminator(sourceType = sourceType, targetType = targetType)!!
}

public fun findDiscriminator(sourceType: Type, targetType: Type): Discriminator? {
    if (sourceType is UnionType && targetType is ShapeType) {
        val candidateDiscriminators = targetType.fields.values.mapNotNull { field ->
            val fieldType = field.type
            if (fieldType is SymbolType) {
                Discriminator(field, fieldType)
            } else {
                null
            }
        }
        return candidateDiscriminators.find { candidateDiscriminator ->
            sourceType.members.all { member ->
                canCoerce(from = member, to = targetType) || run {
                    val memberShape = member as ShapeType
                    val memberField = memberShape.fields[candidateDiscriminator.fieldName]
                    val memberSymbolType = memberField?.type as? SymbolType
                    memberField?.shapeId == candidateDiscriminator.field.shapeId &&
                        memberSymbolType != null &&
                        memberSymbolType != candidateDiscriminator.symbolType
                }
            }
        }
    }
    return null
}
