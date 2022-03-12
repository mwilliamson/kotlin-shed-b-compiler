package org.shedlang.compiler.backends.wasm.tests

import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.backends.tests.*
import org.shedlang.compiler.backends.wasm.*
import org.shedlang.compiler.backends.wasm.runtime.compileRuntime
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.findRoot
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.types.*

object WasmCompilerExecutionEnvironment: StackIrExecutionEnvironment {
    override fun executeInstructions(instructions: List<Instruction>, type: Type, moduleSet: ModuleSet): StackExecutionResult {
        val image = loadModuleSet(moduleSet)
        val compiler = WasmCompiler(image = image, moduleSet = moduleSet)

        val functionContext = compiler.compileInstructions(instructions, WasmFunctionContext.initial())

        val globalContext1 = if (type == StringType) {
            functionContext.addInstruction(Wasm.I.call(WasmNaming.Runtime.print)).toStaticFunctionInGlobalContext(
                identifier = WasmNaming.Wasi.start,
                export = true,
            )
        } else {
            functionContext.toStaticFunctionInGlobalContext(
                identifier = "test",
                export = true,
                results = listOf(Wasm.T.i32),
            )
        }

        val globalContext2 = globalContext1.merge(compileRuntime())
        val globalContext3 = compiler.compileDependencies(globalContext2)
        val compilationResult = globalContext3.toModule()

        temporaryDirectory().use { directory ->
            val wasmPath = directory.path.resolve("test.wasm")
            WasmBackend.compile(
                result = compilationResult,
                target = wasmPath,
                temporaryDirectory = directory.path,
                noEntry = type != StringType,
            )

            if (type == StringType) {
                val testRunnerResult = run(
                    generateWasmCommand(wasmPath, listOf()),
                    workingDirectory = findRoot().resolve("backend-wasm")
                )

                println("stderr:")
                println(testRunnerResult.stderr)

                return StackExecutionResult(
                    value = IrString(testRunnerResult.stdout),
                    stdout = "",
                )
            } else {

                val testRunnerResult = run(
                    listOf(
                        "node",
                        "--experimental-wasi-unstable-preview1",
                        "wasm-test-runner.js",
                        wasmPath.toString()
                    ),
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
}

class WasmCompilerTests: StackIrExecutionTests(WasmCompilerExecutionEnvironment)
