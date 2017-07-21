package org.shedlang.compiler.types

import org.shedlang.compiler.typechecker.canCoerce


interface Effect
object IoEffect : Effect

interface Type {
    val shortDescription: String
}

object UnitType: Type {
    override val shortDescription = "Unit"
}
object BoolType : Type {
    override val shortDescription = "Bool"
}
object IntType : Type {
    override val shortDescription = "Int"
}
object StringType : Type {
    override val shortDescription = "String"
}
object AnyType : Type {
    override val shortDescription = "Any"
}
class MetaType(val type: Type): Type {
    override val shortDescription: String
        get() = throw UnsupportedOperationException()
}
class EffectType(val effect: Effect): Type {
    override val shortDescription: String
        get() = throw UnsupportedOperationException()
}

private var nextTypeParameterId = 0
fun freshTypeParameterId() = nextTypeParameterId++

private var nextAnonymousTypeId = 0
fun freshAnonymousTypeId() = nextAnonymousTypeId++

private var nextShapeId = 0
fun freshShapeId() = nextShapeId++

data class TypeParameter(
    val name: String,
    val typeParameterId: Int = freshTypeParameterId()
): Type {
    override val shortDescription: String
        get() = name
}

data class TypeFunction(
    val parameters: List<TypeParameter>,
    val type: Type
): Type {
    override val shortDescription: String
        get() = throw UnsupportedOperationException()
}

interface HasFieldsType : Type {
    val fields: Map<String, Type>
}

data class ModuleType(
    override val fields: Map<String, Type>
): HasFieldsType {
    override val shortDescription: String
        get() = throw UnsupportedOperationException()
}

data class FunctionType(
    val typeParameters: List<TypeParameter>,
    val positionalArguments: List<Type>,
    val namedArguments: Map<String, Type>,
    val returns: Type,
    val effects: List<Effect>
): Type {
    override val shortDescription: String
        get() = throw UnsupportedOperationException()
}

interface ShapeType: HasFieldsType {
    val name: String
    val shapeId: Int
    val typeArguments: List<Type>
}

data class LazyShapeType(
    override val name: String,
    val getFields: Lazy<Map<String, Type>>,
    override val shapeId: Int = freshShapeId(),
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
        return LazyShapeType(
            name = receiver.type.name,
            getFields = lazy({
                receiver.type.fields.mapValues({ field ->
                    replaceTypes(field.value, typeMap)
                })
            }),
            shapeId = receiver.type.shapeId,
            typeArguments = arguments
        )
    } else if (receiver.type is UnionType) {
        return LazyUnionType(
            name = receiver.type.name,
            getMembers = lazy({
                receiver.type.members.map({ member ->
                    replaceTypes(member, typeMap)
                })
            }),
            typeArguments = arguments
        )
    } else {
        throw UnsupportedOperationException()
    }
}

internal fun replaceTypes(type: Type, typeMap: Map<TypeParameter, Type>): Type {
    if (type is TypeParameter) {
        return typeMap.getOrElse(type, { type })
    } else if (type is UnionType) {
        // TODO: deal with changing name of already applied type parameters
        return LazyUnionType(
            type.name,
            lazy({
                type.members.map({ memberType -> replaceTypes(memberType, typeMap) })
            }),
            typeArguments = type.typeArguments.map({ typeArgument -> replaceTypes(typeArgument, typeMap) })
        )
    } else if (type is ShapeType) {
        // TODO: deal with changing name of already applied type parameters
        return LazyShapeType(
            name = type.name,
            getFields = lazy({
                type.fields.mapValues({ field -> replaceTypes(field.value, typeMap) })
            }),
            shapeId = type.shapeId,
            typeArguments = type.typeArguments.map({ typeArgument -> replaceTypes(typeArgument, typeMap) })
        )
    } else {
        return type
    }
}

private fun appliedTypeShortDescription(name: String, arguments: List<Type>): String {
    val argumentsString = arguments.joinToString(separator = ", ", transform = { type -> type.shortDescription })
    return name + "[" + argumentsString + "]"
}
