package org.shedlang.compiler.frontend.types

import org.shedlang.compiler.ast.UnknownSource
import org.shedlang.compiler.typechecker.TypeCheckError
import org.shedlang.compiler.typechecker.canCoerce
import org.shedlang.compiler.types.*

fun union(left: Type, right: Type): Type {
    if (canCoerce(from = right, to = left)) {
        return left
    } else if (canCoerce(from = left, to = right)) {
        return right
    } else {
        // TODO: should be list of shape types
        fun findMembers(type: Type): Pair<List<ShapeType>, TagField?> {
            return when (type) {
                is UnionType -> Pair(type.members, type.declaredTagField)
                is ShapeType -> Pair(listOf(type), type.tagValue?.tagField)
                else -> Pair(listOf(), null)
            }
        }

        val (leftMembers, leftTagField) = findMembers(left)
        val (rightMembers, rightTagField) = findMembers(right)

        if (leftTagField == null || rightTagField == null || leftTagField != rightTagField) {
            throw TypeCheckError("Cannot union types with different tag fields", source = UnknownSource)
        } else {
            return AnonymousUnionType(members = (leftMembers + rightMembers).distinct(), declaredTagField = leftTagField)
        }
    }
}
