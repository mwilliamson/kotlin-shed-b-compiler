package org.shedlang.compiler

import java.nio.file.Path

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

internal fun <T> Iterable<T>.allIndexed(predicate: (Int, T) -> Boolean): Boolean {
    this.forEachIndexed { index, element ->
        if (!predicate(index, element)) {
            return false
        }
    }
    return true
}

internal fun <T> Iterable<T>.isUnique(): Boolean {
    val list = toList()
    return list.toSet().size == list.size
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


internal fun Path.parents(): Iterable<Path> {
    return object: Iterable<Path> {
        override fun iterator(): Iterator<Path> {
            return ParentPathsIterator(this@parents);
        }
    }
}

private class ParentPathsIterator(var path: Path) : Iterator<Path> {
    override fun hasNext(): Boolean {
        return path.parent != null
    }

    override fun next(): Path {
        path = path.parent
        return path
    }
}

internal class LazyMap<K, V>(private val compute: (K) -> V) {
    private val map = HashMap<K, V>()

    internal fun get(key: K): V {
        val storedValue = map[key]
        if (storedValue == null) {
            val value = compute(key)
            map.put(key, value)
            return value
        } else {
            return storedValue
        }
    }

    internal val values: Collection<V>
        get() = map.values
}
