package org.shedlang.compiler.stackir

fun divideRoundingUp(value: Int, multiple: Int) = (value + multiple - 1) / multiple

fun roundUp(value: Int, multiple: Int): Int {
    val gap = value % multiple
    if (gap == 0) {
        return value
    } else {
        return value + (multiple - gap)
    }
}
