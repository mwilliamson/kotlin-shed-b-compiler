package org.shedlang.compiler.backends.llvm.tests

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.backends.llvm.*
import org.shedlang.compiler.backends.tests.StackIrExecutionEnvironment
import org.shedlang.compiler.backends.tests.StackIrExecutionTests
import org.shedlang.compiler.backends.tests.temporaryDirectory
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.tests.isPair
import org.shedlang.compiler.tests.isSequence
import org.shedlang.compiler.types.*

private val environment = object: StackIrExecutionEnvironment {
    override fun executeInstructions(instructions: List<Instruction>, type: Type, moduleSet: ModuleSet): IrValue {
        val stdout = executeInstructionsOutput(
            instructions,
            moduleSet = moduleSet,
            type = type
        )

        return when (type) {
            BoolType ->
                when (stdout) {
                    "0" -> IrBool(false)
                    "1" -> IrBool(true)
                    else -> throw UnsupportedOperationException()
                }

            CodePointType ->
                IrCodePoint(stdout.toInt())

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
    }

    private fun executeInstructionsOutput(
        instructions: List<Instruction>,
        moduleSet: ModuleSet,
        type: Type
    ): String {
        val image = loadModuleSet(moduleSet)

        val irBuilder = LlvmIrBuilder()
        val libc = LibcCallCompiler(irBuilder = irBuilder)
        val compiler = Compiler(
            image = image,
            moduleSet = moduleSet,
            irBuilder = irBuilder
        )
        val context = compiler.compileInstructions(instructions, context = compiler.startFunction())
        val print = if (type == StringType) {
            val stdoutFd = 1
            val (context2, stringValue) = context.popTemporary()
            val string = LlvmOperandLocal("string")
            val size = LlvmOperandLocal("size")
            val dataPointer = LlvmOperandLocal("dataPointer")
            listOf(
                compiler.rawValueToString(target = string, source = stringValue)
            ) + compiler.stringSize(
                target = size,
                source = string
            ) + listOf(
                compiler.stringDataStart(
                    target = dataPointer,
                    source = string
                ),
                libc.write(
                    fd = LlvmOperandInt(stdoutFd),
                    buf = dataPointer,
                    count = size
                )
            )
        } else {
            val (context2, value) = context.popTemporary()
            listOf(
                LlvmGetElementPtr(
                    target = LlvmOperandLocal("format_int64_pointer"),
                    type = LlvmTypes.arrayType(4, LlvmTypes.i8),
                    pointer = LlvmOperandGlobal("format_int64"),
                    indices = listOf(
                        LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0)),
                        LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0))
                    )
                ),
                LlvmCall(
                    target = null,
                    returnType = LlvmTypes.function(
                        returnType = LlvmTypes.i32,
                        parameterTypes = listOf(LlvmTypes.pointer(LlvmTypes.i8)),
                        hasVarargs = true
                    ),
                    functionPointer = LlvmOperandGlobal("printf"),
                    arguments = listOf(
                        LlvmTypedOperand(LlvmTypes.pointer(LlvmTypes.i8), LlvmOperandLocal("format_int64_pointer")),
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

        val module = LlvmModule(context.topLevelEntities.add(mainFunctionDefinition))

        temporaryDirectory().use { temporaryDirectory ->
            val outputPath = temporaryDirectory.file.toPath().resolve("program.ll")
            val program = serialiseProgram(module) + "@format_int64 = private unnamed_addr constant [4 x i8] c\"%ld\\00\""
            println(withLineNumbers(program))
            outputPath.toFile().writeText(program)
            return executeLlvmInterpreter(outputPath).throwOnError().stdout
        }
    }
}

class LlvmCompilerTests: StackIrExecutionTests(environment)

class StackValueToLlvmOperandTests {
    @Test
    fun falseIsCompiledToZero() {
        val operand = stackValueToLlvmOperand(IrBool(false))

        assertThat(operand, isPair(isSequence(), isLlvmOperandInt(0)))
    }

    @Test
    fun trueIsCompiledToOne() {
        val operand = stackValueToLlvmOperand(IrBool(true))

        assertThat(operand, isPair(isSequence(), isLlvmOperandInt(1)))
    }

    @Test
    fun codePointIsCompiledToImmediateIntegerOperand() {
        val operand = stackValueToLlvmOperand(IrCodePoint(42))

        assertThat(operand, isPair(isSequence(), isLlvmOperandInt(42)))
    }

    @Test
    fun integerIsCompiledToImmediateIntegerOperand() {
        val operand = stackValueToLlvmOperand(IrInt(42))

        assertThat(operand, isPair(isSequence(), isLlvmOperandInt(42)))
    }

    @Test
    fun stringIsCompiledToPointerToGlobal() {
        val operand = stackValueToLlvmOperand(IrString("Hello"))

        assertThat(operand, isPair(

            isSequence(
                isLlvmGlobalDefinition(
                    name = equalTo("string_1"),
                    type = equalTo(LlvmTypes.structure(listOf(
                        LlvmTypes.i64,
                        LlvmTypes.arrayType(5, LlvmTypes.i8)
                    ))),
                    value = isStructureConstant(isSequence(
                        isLlvmTypedOperand(
                            equalTo(LlvmTypes.i64),
                            isLlvmOperandInt(5)
                        ),
                        isLlvmTypedOperand(
                            equalTo(LlvmTypes.arrayType(5, LlvmTypes.i8)),
                            isArrayConstant(isSequence(
                                isLlvmTypedOperand(equalTo(LlvmTypes.i8), isLlvmOperandInt('H'.toInt())),
                                isLlvmTypedOperand(equalTo(LlvmTypes.i8), isLlvmOperandInt('e'.toInt())),
                                isLlvmTypedOperand(equalTo(LlvmTypes.i8), isLlvmOperandInt('l'.toInt())),
                                isLlvmTypedOperand(equalTo(LlvmTypes.i8), isLlvmOperandInt('l'.toInt())),
                                isLlvmTypedOperand(equalTo(LlvmTypes.i8), isLlvmOperandInt('o'.toInt()))
                            ))
                        )
                    )),
                    isConstant = equalTo(true),
                    unnamedAddr = equalTo(true)
                )
            ),
            isLlvmOperandPtrToInt(
                sourceType = equalTo(LlvmTypes.pointer(LlvmTypes.structure(listOf(
                    LlvmTypes.i64,
                    LlvmTypes.arrayType(5, LlvmTypes.i8)
                )))),
                value = isLlvmOperandGlobal("string_1"),
                targetType = equalTo(compiledValueType)
            )
        ))
    }

    @Test
    fun unitIsCompiledToZero() {
        val operand = stackValueToLlvmOperand(IrUnit)

        assertThat(operand, isPair(isSequence(), isLlvmOperandInt(0)))
    }
}

private fun stackValueToLlvmOperand(value: IrValue): Pair<List<LlvmTopLevelEntity>, LlvmOperand> {
    val moduleSet = ModuleSet(setOf())
    val image = loadModuleSet(moduleSet)
    val compiler = Compiler(image = image, moduleSet = moduleSet, irBuilder = LlvmIrBuilder())
    return compiler.stackValueToLlvmOperand(value)
}

private fun isLlvmOperandGlobal(name: String): Matcher<LlvmOperand> {
    return cast(has(LlvmOperandGlobal::name, equalTo(name)))
}

private fun isLlvmOperandInt(value: Int): Matcher<LlvmOperand> {
    return cast(has(LlvmOperandInt::value, equalTo(value)))
}

private fun isLlvmOperandPtrToInt(
    sourceType: Matcher<LlvmType>,
    value: Matcher<LlvmOperand>,
    targetType: Matcher<LlvmType>
): Matcher<LlvmOperand> {
    return cast(allOf(
        has(LlvmOperandPtrToInt::sourceType, sourceType),
        has(LlvmOperandPtrToInt::value, value),
        has(LlvmOperandPtrToInt::targetType, targetType)
    ))
}

private fun isArrayConstant(elements: Matcher<List<LlvmTypedOperand>>): Matcher<LlvmOperand> {
    return cast(has(LlvmOperandArray::elements, elements))
}

private fun isStructureConstant(elements: Matcher<List<LlvmTypedOperand>>): Matcher<LlvmOperand> {
    return cast(has(LlvmOperandStructure::elements, elements))
}

private fun isLlvmTypedOperand(
    type: Matcher<LlvmType>,
    operand: Matcher<LlvmOperand>
): Matcher<LlvmTypedOperand> {
    return allOf(
        has(LlvmTypedOperand::type, type),
        has(LlvmTypedOperand::operand, operand)
    )
}

private fun isLlvmGlobalDefinition(
    name: Matcher<String>,
    type: Matcher<LlvmType>,
    value: Matcher<LlvmOperand>,
    isConstant: Matcher<Boolean>,
    unnamedAddr: Matcher<Boolean>
): Matcher<LlvmTopLevelEntity> {
    return cast(allOf(
        has(LlvmGlobalDefinition::name, name),
        has(LlvmGlobalDefinition::type, type),
        has(LlvmGlobalDefinition::value, value),
        has(LlvmGlobalDefinition::isConstant, isConstant),
        has(LlvmGlobalDefinition::unnamedAddr, unnamedAddr)
    ))
}
