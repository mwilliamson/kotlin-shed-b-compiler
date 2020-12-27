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

        val context1 = compiler.compileInstructions(instructions, WasmFunctionContext.initial(memory = WasmMemory.EMPTY))
        val (context2, printFunc) = generatePrintFunc("print_string", context1)
        val stringEqualsFunc = generateStringEqualsFunc("string_equals")
        val builtins = listOf(printFunc, stringEqualsFunc)

        val context = context2
        if (type == StringType) {
            val module = Wat.module(
                imports = listOf(
                    Wasi.importFdWrite("fd_write"),
                ),
                memoryPageCount = context.memory.pageCount,
                body = context.memory.data + listOf(
                    Wat.func(
                        identifier = "start",
                        body = context.memory.startInstructions,
                    ),
                    Wat.start("start"),
                    Wat.func(
                        "test",
                        exportName = "_start",
                        locals = context.locals.map { local -> Wat.local(local, Wat.i32) },
                        body = context.instructions.add(Wat.I.call("print_string", listOf())),
                    ),
                    *builtins.toTypedArray(),
                ),
            )
            println(withLineNumbers(module.serialise()))

            temporaryDirectory().use { directory ->
                val watPath = directory.path.resolve("test.wat")
                watPath.toFile().writeText(module.serialise())

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
            val module = Wat.module(
                imports = listOf(
                    Wasi.importFdWrite("fd_write"),
                ),
                memoryPageCount = context.memory.pageCount,
                body = context.memory.data + listOf(
                    Wat.func(
                        identifier = "start",
                        body = context.memory.startInstructions,
                    ),
                    Wat.start("start"),
                    Wat.func(
                        "test",
                        exportName = "test",
                        result = Wat.i32,
                        locals = context.locals.map { local -> Wat.local(local, Wat.i32) },
                        body = context.instructions,
                    ),
                    *builtins.toTypedArray(),
                ),
            )
            println(withLineNumbers(module.serialise()))

            temporaryDirectory().use { directory ->
                val watPath = directory.path.resolve("test.wat")
                watPath.toFile().writeText(module.serialise())

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
