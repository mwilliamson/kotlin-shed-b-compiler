package org.shedlang.compiler.backends.wasm.tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.shedlang.compiler.backends.tests.run
import org.shedlang.compiler.backends.tests.temporaryDirectory
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmBinaryFormat
import org.shedlang.compiler.backends.wasm.wasm.WasmLocal
import org.shedlang.compiler.backends.wasm.wasm.WasmModule
import org.shedlang.compiler.tests.Snapshotter
import org.shedlang.compiler.tests.SnapshotterResolver
import java.nio.file.Path

@ExtendWith(SnapshotterResolver::class)
class WasmBinaryFormatTests {
    @Test
    fun emptyModule(snapshotter: Snapshotter) {
        val module = Wasm.module()

        checkModuleSnapshot(module, snapshotter)
    }

    @Test
    fun emptyObjectFile(snapshotter: Snapshotter) {
        val module = Wasm.module()

        checkObjectFileSnapshot(module, snapshotter)
    }

    @Test
    fun typeSection(snapshotter: Snapshotter) {
        val module = Wasm.module(
            types = listOf(
                Wasm.T.funcType(params = listOf(Wasm.T.i32, Wasm.T.i32), results = listOf(Wasm.T.i32)),
            ),
        )

        checkModuleSnapshot(module, snapshotter)
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

        checkModuleSnapshot(module, snapshotter)
    }

    @Test
    fun whenMemoryIsRequiredThenPageCountIsWrittenToMemorySection(snapshotter: Snapshotter) {
        val module = Wasm.module(
            memoryPageCount = 42,
        )

        checkModuleSnapshot(module, snapshotter)
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

        checkModuleSnapshot(module, snapshotter)
    }

    @Test
    fun dataSegment(snapshotter: Snapshotter) {
        val module = Wasm.module(
            memoryPageCount = 1,
            dataSegments = listOf(
                Wasm.dataSegment(42, byteArrayOf(0x88.toByte(), 0x89.toByte()))
            ),
        )

        checkModuleSnapshot(module, snapshotter)
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

        checkModuleSnapshot(module, snapshotter)
    }

    @Test
    fun functionLocalsAreWritten(snapshotter: Snapshotter) {
        val module = Wasm.module(
            types = listOf(
                Wasm.T.funcType(params = listOf(Wasm.T.i32), results = listOf()),
            ),
            functions = listOf(
                Wasm.function(
                    identifier = "FIRST",
                    params = listOf(Wasm.param("arg0", Wasm.T.i32)),
                    results = listOf(),
                    locals = listOf(WasmLocal("local0", Wasm.T.i32)),
                    body = listOf(),
                ),
            ),
        )

        checkModuleSnapshot(module, snapshotter)
    }

    @Test
    fun exportedFunctionsAreWrittenToExportSection(snapshotter: Snapshotter) {
        val module = Wasm.module(
            types = listOf(
                Wasm.T.funcType(params = listOf(), results = listOf()),
            ),
            functions = listOf(
                Wasm.function(
                    identifier = "FIRST",
                    exportName = "EXPORT_FIRST",
                    params = listOf(),
                    results = listOf(),
                    body = listOf(),
                ),
                Wasm.function(
                    identifier = "SECOND",
                    exportName = "EXPORT_SECOND",
                    params = listOf(),
                    results = listOf(),
                    body = listOf(),
                ),
            ),
        )

        checkModuleSnapshot(module, snapshotter)
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

        checkModuleSnapshot(module, snapshotter)
    }

    @Test
    fun tablesAreWrittenUsingTableAndElemSection(snapshotter: Snapshotter) {
        val module = Wasm.module(
            types = listOf(
                Wasm.T.funcType(params = listOf(), results = listOf()),
            ),
            functions = listOf(
                Wasm.function(
                    identifier = "FIRST",
                    params = listOf(),
                    results = listOf(),
                    body = listOf(),
                ),
                Wasm.function(
                    identifier = "SECOND",
                    params = listOf(),
                    results = listOf(),
                    body = listOf(),
                ),
            ),
            table = listOf("SECOND", "FIRST"),
        )

        checkModuleSnapshot(module, snapshotter)
    }

    @Test
    fun globalsHaveSymbolsInObjectFile(snapshotter: Snapshotter) {
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

        checkObjectFileSnapshot(module, snapshotter)
    }

    @Test
    fun functionsHaveSymbolsInObjectFile(snapshotter: Snapshotter) {
        val module = Wasm.module(
            types = listOf(
                Wasm.T.funcType(params = listOf(), results = listOf()),
            ),
            imports = listOf(
                Wasm.importFunction(
                    moduleName = "MODULE",
                    entityName = "ENTITY",
                    identifier = "IMPORTED",
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
                Wasm.function(
                    identifier = "SECOND",
                    params = listOf(),
                    results = listOf(),
                    body = listOf(),
                ),
            ),
        )

        checkObjectFileSnapshot(module, snapshotter)
    }

    private fun checkModuleSnapshot(module: WasmModule, snapshotter: Snapshotter) {
        checkSnapshot(module, snapshotter, objectFile = false)
    }

    private fun checkObjectFileSnapshot(module: WasmModule, snapshotter: Snapshotter) {
        checkSnapshot(module, snapshotter, objectFile = true)
    }

    private fun checkSnapshot(module: WasmModule, snapshotter: Snapshotter, objectFile: Boolean) {
        temporaryDirectory().use { temporaryDirectory ->
            val path = temporaryDirectory.path.resolve("module.wasm")
            path.toFile().outputStream().use { outputStream ->
                if (objectFile) {
                    WasmBinaryFormat.writeObjectFile(module, outputStream, lateIndices = mapOf())
                } else {
                    WasmBinaryFormat.writeModule(module, outputStream, lateIndices = mapOf())
                }
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
