package org.shedlang.compiler.backends.wasm.tests

import org.apiguardian.api.API
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.util.AnnotationUtils.findAnnotation
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.backends.tests.*
import org.shedlang.compiler.backends.wasm.*
import org.shedlang.compiler.backends.wasm.runtime.compileRuntime
import org.shedlang.compiler.backends.wasm.wasm.Wasi
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.Wat
import org.shedlang.compiler.backends.withLineNumbers
import org.shedlang.compiler.findRoot
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.types.*
import java.lang.Exception
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
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

        val module = Wasm.module(
            types = boundGlobalContext.types,
            imports = listOf(
                Wasi.importFdWrite(),
            ),
            globals = boundGlobalContext.globals,
            memoryPageCount = boundGlobalContext.pageCount,
            start = "start",
            dataSegments = boundGlobalContext.dataSegments,
            table = boundGlobalContext.table,
            functions = listOf(
                Wasm.function(
                    identifier = "start",
                    body = boundGlobalContext.startInstructions,
                ),
            ) + boundGlobalContext.functions,
        )
        val wat = Wat(lateIndices = boundGlobalContext.lateIndices)
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

@ExtendWith(WorkInProgressCondition::class)
@WorkInProgress
class WasmCompilerTests: StackIrExecutionTests(WasmCompilerExecutionEnvironment)

@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(RetentionPolicy.RUNTIME)
@API(status = API.Status.STABLE, since = "5.0")
annotation class WorkInProgress

internal class WorkInProgressCondition : ExecutionCondition {
    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        val element = context.element
        val annotation = findAnnotation(element, WorkInProgress::class.java)
        if (annotation.isPresent && isCi()) {
            return ConditionEvaluationResult.disabled("WIP")
        }
        return ENABLED
    }

    private fun isCi(): Boolean {
        val value = System.getenv("CI")
        return value != null && value == "true"
    }

    companion object {
        private val ENABLED = ConditionEvaluationResult.enabled("@WorkInProgress is not present")
    }
}
