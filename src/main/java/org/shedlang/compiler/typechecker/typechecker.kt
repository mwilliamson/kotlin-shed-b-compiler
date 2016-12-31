package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.ExpressionNode

interface Type

object IntType : Type

class TypeContext() {

}

fun inferType(expression: ExpressionNode, context: TypeContext) : Type {
    return IntType
}
