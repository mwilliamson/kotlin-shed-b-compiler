package org.shedlang.compiler.frontend.types

import org.shedlang.compiler.typechecker.canCoerce
import org.shedlang.compiler.types.AnonymousUnionType
import org.shedlang.compiler.types.Type
import org.shedlang.compiler.types.UnionType

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
