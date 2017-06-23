package org.shedlang.compiler.types

import org.shedlang.compiler.typechecker.canCoerce


interface Effect
object IoEffect : Effect

interface Type

object UnitType: Type
object BoolType : Type
object IntType : Type
object StringType : Type
object AnyType : Type
class MetaType(val type: Type): Type
class EffectType(val effect: Effect): Type

private var nextTypeParameterId = 0
fun freshTypeParameterId() = nextTypeParameterId++

private var nextAnonymousTypeId = 0
fun freshAnonymousTypeId() = nextAnonymousTypeId++

class TypeParameter(
    val name: String,
    val typeParameterId: Int = freshTypeParameterId()
): Type

interface HasFieldsType : Type {
    val fields: Map<String, Type>
}

data class ModuleType(
    override val fields: Map<String, Type>
): HasFieldsType

data class FunctionType(
    val typeParameters: List<TypeParameter>,
    val positionalArguments: List<Type>,
    val namedArguments: Map<String, Type>,
    val returns: Type,
    val effects: List<Effect>
): Type

interface ShapeType: HasFieldsType {
    val name: String
}

data class LazyShapeType(
    override val name: String,
    val getFields: Lazy<Map<String, Type>>
): ShapeType {
    override val fields: Map<String, Type> by getFields
}

interface UnionType: Type {
    val name: String;
    val members: List<Type>;
}

data class SimpleUnionType(
    override val name: String,
    override val members: List<Type>
): UnionType


data class AnonymousUnionType(
    override val name: String = "_Union" + freshAnonymousTypeId(),
    override val members: List<Type>
): UnionType

data class LazyUnionType(
    override val name: String,
    private val getMembers: Lazy<List<Type>>
): UnionType {
    override val members: List<Type> by getMembers
}

fun functionType(
    typeParameters: List<TypeParameter> = listOf(),
    positionalArguments: List<Type> = listOf(),
    namedArguments: Map<String, Type> = mapOf(),
    returns: Type,
    effects: List<Effect> = listOf()
) = FunctionType(
    typeParameters = typeParameters,
    positionalArguments = positionalArguments,
    namedArguments = namedArguments,
    returns = returns,
    effects = effects
)

fun positionalFunctionType(arguments: List<Type>, returns: Type)
    = functionType(positionalArguments = arguments, returns = returns)


fun union(left: Type, right: Type): Type {
    // TODO: check coercion the other way round
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
