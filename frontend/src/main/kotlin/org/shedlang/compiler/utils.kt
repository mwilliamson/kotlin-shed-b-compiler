package org.shedlang.compiler

fun <T: Any?> T?.orElseThrow(exception: Exception): T {
    if (this == null) {
        throw exception
    } else {
        return this
    }
}

fun <T: Any?> T?.nullableToList(): List<T> {
    if (this == null) {
        return listOf()
    } else {
        return listOf(this)
    }
}

internal fun Iterable<Boolean>.all(): Boolean {
    for (element in this) {
        if (!element) {
            return false
        }
    }
    return true
}

internal fun <T1, T2, T3, R> zip3(
    iterable1: Iterable<T1>,
    iterable2: Iterable<T2>,
    iterable3: Iterable<T3>,
    func: (T1, T2, T3) -> R
): Iterable<R> {
    val iterator1 = iterable1.iterator()
    val iterator2 = iterable2.iterator()
    val iterator3 = iterable3.iterator()
    val list = ArrayList<R>(minOf(
        iterable1.collectionSizeOrDefault(10),
        iterable2.collectionSizeOrDefault(10),
        iterable3.collectionSizeOrDefault(10)
        ))
    while (iterator1.hasNext() && iterator2.hasNext() && iterator3.hasNext()) {
        list.add(func(iterator1.next(), iterator2.next(), iterator3.next()))
    }
    return list
}

private fun <T> Iterable<T>.collectionSizeOrDefault(default: Int): Int = if (this is Collection<*>) this.size else default
