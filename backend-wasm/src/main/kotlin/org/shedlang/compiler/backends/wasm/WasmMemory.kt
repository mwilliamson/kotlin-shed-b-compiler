package org.shedlang.compiler.backends.wasm

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.shedlang.compiler.backends.wasm.wasm.*
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmDataSegment
import org.shedlang.compiler.backends.wasm.wasm.WasmInstruction
import org.shedlang.compiler.backends.wasm.wasm.Wat

internal data class WasmMemory(
    internal val size: Int,
    internal val dataSegments: PersistentList<WasmDataSegment>,
    internal val startInstructions: PersistentList<WasmInstruction>,
) {
    companion object {
        val EMPTY = WasmMemory(
            size = 0,
            dataSegments = persistentListOf(),
            startInstructions = persistentListOf(),
        )

        val PAGE_SIZE = 65536
    }

    internal val pageCount: Int
        get() = (size + PAGE_SIZE - 1) / PAGE_SIZE

    fun addStartInstructions(vararg instructions: WasmInstruction): WasmMemory {
        return copy(startInstructions = startInstructions.addAll(instructions.toList()))
    }

    fun staticAllocString(value: String): Pair<WasmMemory, Int> {
        val byteArray = value.toByteArray(Charsets.UTF_8)
        val newContext = copy(
            size = size + byteArray.size,
            dataSegments = dataSegments.add(Wasm.dataSegment(size, byteArray)),
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
                Wasm.I.i32Store(aligned.size, value),
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
