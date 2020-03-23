package org.shedlang.compiler.backends.llvm.tests

import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.backends.llvm.*
import org.shedlang.compiler.backends.tests.StackExecutionResult
import org.shedlang.compiler.backends.tests.StackIrExecutionEnvironment
import org.shedlang.compiler.backends.tests.temporaryDirectory
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.types.*

object LlvmCompilerExecutionEnvironment: StackIrExecutionEnvironment {
    override fun executeInstructions(instructions: List<Instruction>, type: Type, moduleSet: ModuleSet): StackExecutionResult {
        val image = loadModuleSet(moduleSet)

        val irBuilder = LlvmIrBuilder()
        val libc = LibcCallCompiler(irBuilder = irBuilder)
        val closures = ClosureCompiler(irBuilder = irBuilder, libc = libc)
        val modules = ModuleValueCompiler(irBuilder = irBuilder, moduleSet = moduleSet)
        val strings = StringCompiler(irBuilder = irBuilder, libc = libc)
        val builtins = BuiltinModuleCompiler(
            moduleSet = moduleSet,
            irBuilder = irBuilder,
            closures = closures,
            libc = libc,
            modules = modules,
            strings = strings
        )
        val compiler = Compiler(
            image = image,
            moduleSet = moduleSet,
            irBuilder = irBuilder
        )
        val context = compiler.compileInstructions(instructions, context = compiler.startFunction())

        val print = if (type == StringType) {
            val (context2, stringValue) = context.popTemporary()
            listOf(
                LlvmGetElementPtr(
                    target = LlvmOperandLocal("boundary_pointer"),
                    pointerType = LlvmTypes.pointer(LlvmTypes.arrayType(17, LlvmTypes.i8)),
                    pointer = LlvmOperandGlobal("boundary"),
                    indices = listOf(
                        LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0)),
                        LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0))
                    )
                ),
                libc.write(
                    fd = LlvmOperandInt(1),
                    buf = LlvmOperandLocal("boundary_pointer"),
                    count = LlvmOperandInt(16)
                )
            ) + builtins.print(stringValue)
        } else {
            val (context2, value) = context.popTemporary()
            listOf(
                LlvmGetElementPtr(
                    target = LlvmOperandLocal("boundary_pointer"),
                    pointerType = LlvmTypes.pointer(LlvmTypes.arrayType(17, LlvmTypes.i8)),
                    pointer = LlvmOperandGlobal("boundary"),
                    indices = listOf(
                        LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0)),
                        LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0))
                    )
                ),
                libc.printf(
                    target = null,
                    format = LlvmOperandLocal("boundary_pointer"),
                    args = listOf()
                ),
                LlvmGetElementPtr(
                    target = LlvmOperandLocal("format_int64_pointer"),
                    pointerType = LlvmTypes.pointer(LlvmTypes.arrayType(4, LlvmTypes.i8)),
                    pointer = LlvmOperandGlobal("format_int64"),
                    indices = listOf(
                        LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0)),
                        LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0))
                    )
                ),
                libc.printf(
                    target = null,
                    format = LlvmOperandLocal("format_int64_pointer"),
                    args = listOf(
                        LlvmTypedOperand(compiledValueType, value)
                    )
                )
            )
        }
        val mainFunctionDefinition = LlvmFunctionDefinition(
            name = "main",
            returnType = LlvmTypes.i64,
            parameters = listOf(),
            body = context.instructions + print + listOf(
                LlvmReturn(LlvmTypes.i64, LlvmOperandInt(0))
            )
        )

        val module = LlvmModule(
            context.topLevelEntities
                .add(mainFunctionDefinition)
                .addAll(libc.declarations())
        )

        temporaryDirectory().use { temporaryDirectory ->
            val outputPath = temporaryDirectory.file.toPath().resolve("program.ll")
            val program = serialiseProgram(module) + """
                @boundary = private unnamed_addr constant [17 x i8] c"=== BOUNDARY ===\00"
                @format_int64 = private unnamed_addr constant [4 x i8] c"%ld\00"
            """.trimIndent()
            println(withLineNumbers(program))
            outputPath.toFile().writeText(program)
            val stdout = executeLlvmInterpreter(outputPath, linkerFiles = compiler.linkerFiles()).throwOnError().stdout
            val parts = stdout.split("=== BOUNDARY ===")
            return StackExecutionResult(
                value = stdoutToIrValue(parts[1], type = type),
                stdout = parts[0]
            )
        }
    }

    private fun stdoutToIrValue(stdout: String, type: Type): IrValue {
        val irValue = when (type) {
            BoolType ->
                when (stdout) {
                    "0" -> IrBool(false)
                    "1" -> IrBool(true)
                    else -> throw UnsupportedOperationException()
                }

            UnicodeScalarType ->
                IrUnicodeScalar(stdout.toInt())

            IntType ->
                IrInt(stdout.toBigInteger())

            StringType ->
                IrString(stdout)

            UnitType ->
                when (stdout) {
                    "0" -> IrUnit
                    else -> throw UnsupportedOperationException()
                }

            else ->
                throw java.lang.UnsupportedOperationException("unsupported type: ${type.shortDescription}")
        }
        return irValue
    }
}
