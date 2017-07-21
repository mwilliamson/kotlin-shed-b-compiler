package org.shedlang.compiler.types

import org.shedlang.compiler.typechecker.canCoerce


interface Effect
object IoEffect : Effect

interface Type {
    val name: String
}

object UnitType: Type {
    override val name = "Unit"
}
object BoolType : Type {
    override val name = "Bool"
}
object IntType : Type {
    override val name = "Int"
}
object StringType : Type {
    override val name = "String"
}
object AnyType : Type {
    override val name = "Any"
}
class MetaType(val type: Type): Type {
    override val name: String
        get() = throw UnsupportedOperationException()
}
class EffectType(val effect: Effect): Type {
    override val name: String
        get() = throw UnsupportedOperationException()
}

private var nextTypeParameterId = 0
fun freshTypeParameterId() = nextTypeParameterId++

private var nextAnonymousTypeId = 0
fun freshAnonymousTypeId() = nextAnonymousTypeId++

class TypeParameter(
    override val name: String,
    val typeParameterId: Int = freshTypeParameterId()
): Type

data class TypeFunction(
    val parameters: List<TypeParameter>,
    val type: Type
): Type {
    override val name: String
        get() = throw UnsupportedOperationException()
}

interface HasFieldsType : Type {
    val fields: Map<String, Type>
}

data class ModuleType(
    override val fields: Map<String, Type>
): HasFieldsType {
    override val name: String
        get() = throw UnsupportedOperationException()
}

data class FunctionType(
    val typeParameters: List<TypeParameter>,
    val positionalArguments: List<Type>,
    val namedArguments: Map<String, Type>,
    val returns: Type,
    val effects: List<Effect>
): Type {
    override val name: String
        get() = throw UnsupportedOperationException()
}

interface ShapeType: HasFieldsType {
    override val name: String
}

data class LazyShapeType(
    override val name: String,
    val getFields: Lazy<Map<String, Type>>
): ShapeType {
    override val fields: Map<String, Type> by getFields
}

interface UnionType: Type {
    override val name: String;
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

    if (receiver.type is ShapeType) {
        val fields = receiver.type.fields.mapValues({ field ->
            replaceTypes(field.value, typeMap)
        })
        return LazyShapeType(
            name = receiver.type.name + "[" + arguments.joinToString(separator = ", ", transform = { type -> type.name }) + "]",
            getFields = lazy({ fields })
        )
    } else {
        throw UnsupportedOperationException()
    }
}

private fun replaceTypes(type: Type, typeMap: Map<TypeParameter, Type>): Type {
    if (type is TypeParameter) {
        return typeMap.getOrElse(type, { type })
    } else {
        return type
    }
}
