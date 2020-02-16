package org.shedlang.compiler.backends.llvm

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

internal class PersistentMultiMap<K, V>(private val map: PersistentMap<K, PersistentList<V>>) {
    fun add(key: K, value: V): PersistentMultiMap<K, V> {
        return PersistentMultiMap(map.put(key, get(key).add(value)))
    }

    operator fun get(key: K): PersistentList<V> {
        return map.getOrDefault(key, persistentListOf())
    }
}

internal fun <K, V> persistentMultiMapOf(): PersistentMultiMap<K, V> {
    return PersistentMultiMap(persistentMapOf())
}
