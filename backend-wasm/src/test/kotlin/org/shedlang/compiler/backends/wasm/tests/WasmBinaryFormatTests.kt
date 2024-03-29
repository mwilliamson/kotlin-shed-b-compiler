package org.shedlang.compiler.backends.wasm.tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.shedlang.compiler.backends.tests.run
import org.shedlang.compiler.backends.tests.temporaryDirectory
import org.shedlang.compiler.backends.wasm.wasm.*
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
    fun tagsAreWrittenToTagSection(snapshotter: Snapshotter) {
        val module = Wasm.module(
            types = listOf(
                Wasm.T.funcType(listOf(Wasm.T.i32), listOf()),
                Wasm.T.funcType(listOf(Wasm.T.i32, Wasm.T.i32), listOf()),
            ),
            tags = listOf(
                Wasm.tag(
                    identifier = "FIRST",
                    type = Wasm.T.funcType(listOf(Wasm.T.i32), listOf()),
                ),
                Wasm.tag(
                    identifier = "SECOND",
                    type = Wasm.T.funcType(listOf(Wasm.T.i32, Wasm.T.i32), listOf()),
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
                Wasm.dataSegment(44, 4, byteArrayOf(0x88.toByte(), 0x89.toByte()))
            ),
        )

        checkModuleSnapshot(module, snapshotter)
    }

    @Test
    fun dataSegmentWithoutBytesIsInitialisedToZero(snapshotter: Snapshotter) {
        val module = Wasm.module(
            memoryPageCount = 1,
            dataSegments = listOf(
                Wasm.dataSegmentZeroed(offset = 44, alignment = 4, size = 8)
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
    fun functionLocalsHaveName(snapshotter: Snapshotter) {
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
                    body = listOf(
                        Wasm.I.localGet("local0"),
                        Wasm.I.localGet("arg0"),
                        Wasm.I.drop,
                        Wasm.I.drop,
                    ),
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
                    export = true,
                    params = listOf(),
                    results = listOf(),
                    body = listOf(),
                ),
                Wasm.function(
                    identifier = "SECOND",
                    export = true,
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
    fun whenMemoryIsRequiredThenPageCountIsWrittenToMemoryImportInObjectFile(snapshotter: Snapshotter) {
        val module = Wasm.module(
            memoryPageCount = 42,
        )

        checkObjectFileSnapshot(module, snapshotter)
    }

    @Test
    fun dataSegmentIsPresentInLinkingSectionInObjectFile(snapshotter: Snapshotter) {
        val module = Wasm.module(
            memoryPageCount = 1,
            dataSegments = listOf(
                Wasm.dataSegment(44, 4, byteArrayOf(0x88.toByte(), 0x89.toByte()), name = null),
                Wasm.dataSegment(48, 4, byteArrayOf(0x98.toByte(), 0x99.toByte()), name = "SOME_DATA"),
            ),
        )

        checkObjectFileSnapshot(module, snapshotter)
    }

    @Test
    fun dataAddressesHaveRelocationEntries(snapshotter: Snapshotter) {
        val dataSegment = Wasm.dataSegment(44, 4, byteArrayOf(0x88.toByte(), 0x89.toByte()))

        val module = Wasm.module(
            memoryPageCount = 1,
            dataSegments = listOf(
                dataSegment
            ),
            types = listOf(Wasm.T.funcType(params = listOf(), results = listOf())),
            functions = listOf(
                Wasm.function(
                    identifier = "FIRST",
                    params = listOf(),
                    results = listOf(),
                    body = listOf(
                        Wasm.I.i32Const(dataSegment.key),
                        Wasm.I.drop,
                    ),
                ),
            ),
        )

        checkObjectFileSnapshot(module, snapshotter)
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

    @Test
    fun tagsHaveSymbolsInObjectFile(snapshotter: Snapshotter) {
        val module = Wasm.module(
            types = listOf(
                Wasm.T.funcType(listOf(Wasm.T.i32), listOf()),
                Wasm.T.funcType(listOf(Wasm.T.i32, Wasm.T.i32), listOf()),
            ),
            tags = listOf(
                Wasm.tag(
                    identifier = "FIRST",
                    type = Wasm.T.funcType(listOf(Wasm.T.i32), listOf()),
                ),
                Wasm.tag(
                    identifier = "SECOND",
                    type = Wasm.T.funcType(listOf(Wasm.T.i32, Wasm.T.i32), listOf()),
                ),
            ),
        )

        checkObjectFileSnapshot(module, snapshotter)
    }

    @Test
    fun functionIndicesInCodeSectionAreRelocated(snapshotter: Snapshotter) {
        val module = Wasm.module(
            types = listOf(
                Wasm.T.funcType(params = listOf(), results = listOf()),
            ),
            functions = listOf(
                Wasm.function(
                    identifier = "FIRST",
                    params = listOf(),
                    results = listOf(),
                    body = listOf(
                        Wasm.I.call("FIRST"),
                    ),
                ),
            ),
        )

        checkObjectFileSnapshot(module, snapshotter)
    }

    @Test
    fun globalIndicesInCodeSectionAreRelocated(snapshotter: Snapshotter) {
        val module = Wasm.module(
            types = listOf(
                Wasm.T.funcType(params = listOf(), results = listOf()),
            ),
            globals = listOf(
                Wasm.global("GLOBAL", mutable = false, type = Wasm.T.i32, value = Wasm.I.i32Const(0))
            ),
            functions = listOf(
                Wasm.function(
                    identifier = "FIRST",
                    params = listOf(),
                    results = listOf(),
                    body = listOf(
                        Wasm.I.globalGet("GLOBAL"),
                        Wasm.I.drop,
                    ),
                ),
            ),
        )

        checkObjectFileSnapshot(module, snapshotter)
    }

    @Test
    fun tagIndicesInCodeSectionAreRelocated(snapshotter: Snapshotter) {
        val module = Wasm.module(
            types = listOf(
                Wasm.T.funcType(params = listOf(), results = listOf()),
            ),
            tags = listOf(
                Wasm.tag("TAG", type = Wasm.T.funcType(params = listOf(), results = listOf())),
            ),
            functions = listOf(
                Wasm.function(
                    identifier = "FIRST",
                    params = listOf(),
                    results = listOf(),
                    body = listOf(
                        Wasm.I.throw_("TAG"),
                    ),
                ),
            ),
        )

        checkObjectFileSnapshot(module, snapshotter)
    }

    @Test
    fun typeIndicesInCodeSectionAreRelocated(snapshotter: Snapshotter) {
        val module = Wasm.module(
            types = listOf(
                Wasm.T.funcType(params = listOf(), results = listOf()),
            ),
            table = listOf("FIRST"),
            functions = listOf(
                Wasm.function(
                    identifier = "FIRST",
                    params = listOf(),
                    results = listOf(),
                    body = listOf(
                        Wasm.I.callIndirect(
                            type = Wasm.T.funcType(params = listOf(), results = listOf()),
                            tableIndex = Wasm.I.i32Const(0),
                            args = listOf(),
                        ),
                    ),
                ),
            ),
        )

        checkObjectFileSnapshot(module, snapshotter)
    }

    @Test
    fun exportedFunctionsAreWrittenToSymbolSectionWithExportedFlag(snapshotter: Snapshotter) {
        val module = Wasm.module(
            types = listOf(
                Wasm.T.funcType(params = listOf(), results = listOf()),
            ),
            functions = listOf(
                Wasm.function(
                    identifier = "FIRST",
                    export = true,
                    params = listOf(),
                    results = listOf(),
                    body = listOf(),
                ),
                Wasm.function(
                    identifier = "SECOND",
                    export = true,
                    params = listOf(),
                    results = listOf(),
                    body = listOf(),
                ),
            ),
        )

        checkObjectFileSnapshot(module, snapshotter)
    }

    @Test
    fun tablesAreWrittenUsingTableImportAndElemSectionInObjectFile(snapshotter: Snapshotter) {
        val module = Wasm.module(
            types = listOf(
                Wasm.T.funcType(params = listOf(), results = listOf()),
            ),
            functions = listOf(
                Wasm.function(
                    identifier = "FIRST",
                    params = listOf(),
                    results = listOf(),
                    body = listOf(
                        Wasm.I.callIndirect(
                            type = Wasm.T.funcType(params = listOf(), results = listOf()),
                            tableIndex = Wasm.I.i32Const(WasmConstValue.TableEntryIndex("SECOND")),
                            args = listOf(),
                        ),
                    ),
                ),
                Wasm.function(
                    identifier = "SECOND",
                    params = listOf(),
                    results = listOf(),
                    body = listOf(
                        Wasm.I.callIndirect(
                            type = Wasm.T.funcType(params = listOf(), results = listOf()),
                            tableIndex = Wasm.I.i32Const(WasmConstValue.TableEntryIndex("FIRST")),
                            args = listOf(),
                        ),
                    ),
                ),
            ),
            table = listOf("SECOND", "FIRST"),
        )

        checkObjectFileSnapshot(module, snapshotter)
    }

    @Test
    fun startFunctionIsWrittenToInitFuncsSubSection(snapshotter: Snapshotter) {
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

        checkObjectFileSnapshot(module, snapshotter)
    }

    private fun checkModuleSnapshot(module: WasmModule, snapshotter: Snapshotter) {
        checkSnapshot(module, snapshotter, objectFile = false)
    }

    private fun checkObjectFileSnapshot(
        module: WasmModule,
        snapshotter: Snapshotter,
    ) {
        checkSnapshot(module, snapshotter, objectFile = true)
    }

    private fun checkSnapshot(
        module: WasmModule,
        snapshotter: Snapshotter,
        objectFile: Boolean,
    ) {
        temporaryDirectory().use { temporaryDirectory ->
            val path = temporaryDirectory.path.resolve("module.wasm")
            path.toFile().outputStream().use { outputStream ->
                if (objectFile) {
                    WasmBinaryFormat.writeObjectFile(
                        module,
                        outputStream,
                        tagValuesToInt = mapOf(),
                    )
                } else {
                    WasmBinaryFormat.writeModule(
                        module,
                        outputStream,
                        tagValuesToInt = mapOf(),
                    )
                }
            }

            validate(path)

            snapshotter.assertSnapshot(objdump(path))
        }
    }

    private fun validate(path: Path) {
        run(listOf("wasm-validate", "--enable-exceptions", path.toString())).throwOnError()
    }

    private fun objdump(path: Path): String {
        return run(listOf("wasm-objdump", "-dx", path.toString())).throwOnError().stdout
    }
}
