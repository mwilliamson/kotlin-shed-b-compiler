package org.shedlang.compiler.backends.wasm.tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.shedlang.compiler.backends.tests.run
import org.shedlang.compiler.backends.tests.temporaryDirectory
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmBinaryFormat
import org.shedlang.compiler.backends.wasm.wasm.WasmModule
import org.shedlang.compiler.tests.Snapshotter
import org.shedlang.compiler.tests.SnapshotterResolver
import java.nio.file.Path

@ExtendWith(SnapshotterResolver::class)
class WasmBinaryFormatTests {
    @Test
    fun emptyModule(snapshotter: Snapshotter) {
        val module = Wasm.module()

        checkSnapshot(module, snapshotter)
    }

    @Test
    fun typeSection(snapshotter: Snapshotter) {
        val module = Wasm.module(
            types = listOf(
                Wasm.T.funcType(params = listOf(Wasm.T.i32, Wasm.T.i32), results = listOf(Wasm.T.i32)),
            ),
        )

        checkSnapshot(module, snapshotter)
    }

    @Test
    fun functionImport(snapshotter: Snapshotter) {
        val module = Wasm.module(
            // Multiple types to make sure we pick the right index
            types = listOf(
                Wasm.T.funcType(params = listOf(Wasm.T.i32, Wasm.T.i32), results = listOf()),
                Wasm.T.funcType(params = listOf(Wasm.T.i32, Wasm.T.i32), results = listOf(Wasm.T.i32)),
                Wasm.T.funcType(params = listOf(Wasm.T.i32), results = listOf()),
            ),
            imports = listOf(
                Wasm.importFunction(
                    moduleName = "MODULE",
                    entityName = "ENTITY",
                    identifier = "DONT_CARE",
                    params = listOf(Wasm.T.i32, Wasm.T.i32),
                    results = listOf(Wasm.T.i32),
                ),
            ),
        )

        checkSnapshot(module, snapshotter)
    }

    @Test
    fun memory(snapshotter: Snapshotter) {
        val module = Wasm.module(
            memoryPageCount = 42,
        )

        checkSnapshot(module, snapshotter)
    }

    @Test
    fun dataSegment(snapshotter: Snapshotter) {
        val module = Wasm.module(
            memoryPageCount = 1,
            dataSegments = listOf(
                Wasm.dataSegment(42, byteArrayOf(0x88.toByte(), 0x89.toByte()))
            ),
        )

        checkSnapshot(module, snapshotter)
    }

    private fun checkSnapshot(module: WasmModule, snapshotter: Snapshotter) {
        temporaryDirectory().use { temporaryDirectory ->
            val path = temporaryDirectory.path.resolve("module.wasm")
            path.toFile().outputStream().use { outputStream ->
                WasmBinaryFormat.write(module, outputStream)
            }

            validate(path)

            snapshotter.assertSnapshot(objdump(path))
        }
    }

    private fun validate(path: Path) {
        run(listOf("wasm-validate", path.toString())).throwOnError()
    }

    private fun objdump(path: Path): String {
        return run(listOf("wasm-objdump", "-dx", path.toString())).throwOnError().stdout
    }
}
