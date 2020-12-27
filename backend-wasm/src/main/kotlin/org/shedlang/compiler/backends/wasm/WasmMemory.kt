package org.shedlang.compiler.backends.wasm

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

private val PAGE_SIZE = 65536

internal data class WasmMemory(
    internal val size: Int,
    internal val data: PersistentList<SExpression>,
    internal val startInstructions: PersistentList<SExpression>,
) {
    companion object {
        val EMPTY = WasmMemory(
            size = 0,
            data = persistentListOf(),
            startInstructions = persistentListOf(),
        )
    }

    internal val pageCount: Int
        get() = (size + PAGE_SIZE - 1) / PAGE_SIZE

    fun staticAllocString(value: String): Pair<WasmMemory, Int> {
        val newContext = copy(
            size = size + value.toByteArray(Charsets.UTF_8).size,
            data = data.add(Wat.data(size, value)),
        )
        return Pair(newContext, size)
    }

    fun staticAllocI32(): Pair<WasmMemory, Int> {
        val aligned = align(4)
        val newContext = aligned.copy(
            size = aligned.size + 4,
        )
        return Pair(newContext, aligned.size)
    }

    fun staticAllocI32(value: Int): Pair<WasmMemory, Int> {
        val aligned = align(4)
        val newContext = aligned.copy(
            size = aligned.size + 4,
            startInstructions = aligned.startInstructions.add(
                Wat.I.i32Store(Wat.I.i32Const(aligned.size), Wat.I.i32Const(value)),
            ),
        )
        return Pair(newContext, aligned.size)
    }

    private fun align(alignment: Int): WasmMemory {
        val misalignment = size % alignment
        if (misalignment == 0) {
            return this
        } else {
            return copy(size = size + (alignment - misalignment))
        }
    }
}
