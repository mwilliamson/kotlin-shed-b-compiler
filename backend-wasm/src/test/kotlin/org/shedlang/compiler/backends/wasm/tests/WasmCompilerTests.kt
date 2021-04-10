package org.shedlang.compiler.backends.wasm.tests

import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.backends.tests.*
import org.shedlang.compiler.backends.wasm.WasmCompiler
import org.shedlang.compiler.backends.wasm.WasmFunctionContext
import org.shedlang.compiler.backends.wasm.WasmNaming
import org.shedlang.compiler.backends.wasm.runtime.compileRuntime
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmSymbolTable
import org.shedlang.compiler.backends.wasm.wasm.Wat
import org.shedlang.compiler.backends.withLineNumbers
import org.shedlang.compiler.findRoot
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.types.*
import java.nio.file.Path


object WasmCompilerExecutionEnvironment: StackIrExecutionEnvironment {
    override fun executeInstructions(instructions: List<Instruction>, type: Type, moduleSet: ModuleSet): StackExecutionResult {
        val image = loadModuleSet(moduleSet)
        val compiler = WasmCompiler(image = image, moduleSet = moduleSet)

        val functionContext = compiler.compileInstructions(instructions, WasmFunctionContext.initial())

        val globalContext1 = if (type == StringType) {
            functionContext.addInstruction(Wasm.I.call(WasmNaming.Runtime.print)).toStaticFunctionInGlobalContext(
                identifier = "test",
                exportName = "_start",
            )
        } else {
            functionContext.toStaticFunctionInGlobalContext(
                identifier = "test",
                exportName = "test",
                results = listOf(Wasm.T.i32),
            )
        }

        val globalContext2 = globalContext1.merge(compileRuntime())
        val globalContext3 = compiler.compileDependencies(globalContext2)
        val boundGlobalContext = globalContext3.bind()

        val module = boundGlobalContext.toModule()
        val wat = Wat(lateIndices = boundGlobalContext.lateIndices, symbolTable = WasmSymbolTable.forModule(module))
        val watContents = wat.serialise(module)
        println(withLineNumbers(watContents))

        if (type == StringType) {

            temporaryDirectory().use { directory ->
                val watPath = directory.path.resolve("test.wat")
                watPath.toFile().writeText(watContents)

                val wasmPath = directory.path.resolve("test.wasm")
                watToWasm(watPath, wasmPath)

                val testRunnerResult = run(
                    listOf("wasmtime", wasmPath.toString()),
                    workingDirectory = findRoot().resolve("backend-wasm")
                )

                println("stderr:")
                println(testRunnerResult.stderr)

                return StackExecutionResult(
                    value = IrString(testRunnerResult.stdout),
                    stdout = "",
                )
            }
        } else {
            temporaryDirectory().use { directory ->
                val watPath = directory.path.resolve("test.wat")
                watPath.toFile().writeText(watContents)

                val wasmPath = directory.path.resolve("test.wasm")
                watToWasm(watPath, wasmPath)

                val testRunnerResult = run(
                    listOf("node", "--experimental-wasi-unstable-preview1", "wasm-test-runner.js", wasmPath.toString()),
                    workingDirectory = findRoot().resolve("backend-wasm")
                )

                println("stderr:")
                println(testRunnerResult.stderr)

                val value = readStdout(testRunnerResult.stdout, type = type)

                return StackExecutionResult(
                    value = value,
                    stdout = "",
                )
            }
        }
    }

    private fun readStdout(stdout: String, type: Type): IrValue {
        return when (type) {
            BoolType ->
                when (stdout) {
                    "0" -> IrBool(false)
                    "1" -> IrBool(true)
                    else -> throw Exception("unexpected stdout: $stdout")
                }

            IntType ->
                IrInt(stdout.toBigInteger())

            UnicodeScalarType ->
                IrUnicodeScalar(stdout.toInt())

            UnitType ->
                when (stdout) {
                    "0" -> IrUnit
                    else -> throw Exception("unexpected stdout: $stdout")
                }

            else ->
                throw Exception("unhandled type: ${type.shortDescription}")
        }
    }

    private fun watToWasm(watPath: Path, wasmPath: Path) {
        val arguments = listOf(
            "wat2wasm",
            watPath.toAbsolutePath().toString(),
            "-o", wasmPath.toAbsolutePath().toString(),
        )
        val watToWasmResult = run(arguments)
        watToWasmResult.throwOnError()
    }
}

class WasmCompilerTests: StackIrExecutionTests(WasmCompilerExecutionEnvironment)
