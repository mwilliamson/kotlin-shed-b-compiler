package org.shedlang.compiler.types

interface TypeLevelValue {
    val shortDescription: String
    fun <T> accept(visitor: Visitor<T>): T

    interface Visitor<T> {
        fun visit(effect: Effect): T
        fun visit(value: ParameterizedTypeLevelValue): T
        fun visit(type: Type): T
        fun visit(value: CastableTypeLevelFunction): T
        fun visit(value: MetaTypeTypeLevelFunction): T
    }
}
