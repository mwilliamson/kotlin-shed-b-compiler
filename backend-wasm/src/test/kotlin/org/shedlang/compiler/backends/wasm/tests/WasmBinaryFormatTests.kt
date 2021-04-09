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
    fun whenMemoryIsRequiredThenPageCountIsWrittenToMemorySection(snapshotter: Snapshotter) {
        val module = Wasm.module(
            memoryPageCount = 42,
        )

        checkSnapshot(module, snapshotter)
    }

    @Test
    fun globalsAreWrittenToGlobalSection(snapshotter: Snapshotter) {
        val module = Wasm.module(
            globals = listOf(
                Wasm.global(
                    identifier = "FIRST",
                    mutable = true,
                    type = Wasm.T.i32,
                    value = Wasm.I.i32Const(42),
                ),
                Wasm.global(
                    identifier = "SECOND",
                    mutable = false,
                    type = Wasm.T.i32,
                    value = Wasm.I.i32Const(47),
                ),
            ),
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

    @Test
    fun functionsAreWrittenToFuncAndCodeSections(snapshotter: Snapshotter) {
        val module = Wasm.module(
            types = listOf(
                Wasm.T.funcType(params = listOf(Wasm.T.i32, Wasm.T.i32), results = listOf(Wasm.T.i32)),
                Wasm.T.funcType(params = listOf(Wasm.T.i32), results = listOf()),
            ),
            functions = listOf(
                Wasm.function(
                    identifier = "FIRST",
                    params = listOf(Wasm.param("arg0", Wasm.T.i32)),
                    results = listOf(),
                    body = listOf(),
                ),
                Wasm.function(
                    identifier = "SECOND",
                    params = listOf(Wasm.param("arg0", Wasm.T.i32), Wasm.param("arg1", Wasm.T.i32)),
                    results = listOf(Wasm.T.i32),
                    body = listOf(
                        Wasm.I.i32Const(42),
                    ),
                ),
            ),
        )

        checkSnapshot(module, snapshotter)
    }

    @Test
    fun startFunctionIsWrittenToStartSection(snapshotter: Snapshotter) {
        val module = Wasm.module(
            types = listOf(
                Wasm.T.funcType(params = listOf(), results = listOf()),
            ),
            // include an import to make sure we index funcs properly
            imports = listOf(
                Wasm.importFunction(
                    moduleName = "MODULE",
                    entityName = "ENTITY",
                    identifier = "DONT_CARE",
                    params = listOf(),
                    results = listOf(),
                ),
            ),
            functions = listOf(
                Wasm.function(
                    identifier = "FIRST",
                    params = listOf(),
                    results = listOf(),
                    body = listOf(),
                ),
            ),
            start = "FIRST",
        )

        checkSnapshot(module, snapshotter)
    }

    private fun checkSnapshot(module: WasmModule, snapshotter: Snapshotter) {
        temporaryDirectory().use { temporaryDirectory ->
            val path = temporaryDirectory.path.resolve("module.wasm")
            path.toFile().outputStream().use { outputStream ->
                WasmBinaryFormat.write(module, outputStream, lateIndices = mapOf())
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
