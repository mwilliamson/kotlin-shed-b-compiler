package org.shedlang.compiler.types

import org.shedlang.compiler.ast.Identifier

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
