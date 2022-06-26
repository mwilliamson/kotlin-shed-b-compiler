package org.shedlang.compiler.types

interface StaticValue {
    val shortDescription: String
    fun <T> acceptStaticValueVisitor(visitor: Visitor<T>): T

    interface Visitor<T> {
        fun visit(effect: Effect): T
        fun visit(value: ParameterizedStaticValue): T
        fun visit(type: Type): T
        fun visit(value: CastableTypeFunction): T
        fun visit(value: MetaTypeTypeFunction): T
    }
}
