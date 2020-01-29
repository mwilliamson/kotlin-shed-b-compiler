package org.shedlang.compiler.backends.llvm.tests

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import kotlinx.collections.immutable.PersistentList
import org.junit.jupiter.api.Test
import org.shedlang.compiler.EMPTY_TYPES
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.ExpressionNode
import org.shedlang.compiler.backends.llvm.*
import org.shedlang.compiler.backends.tests.StackIrExecutionEnvironment
import org.shedlang.compiler.backends.tests.StackIrExecutionTests
import org.shedlang.compiler.backends.tests.loader
import org.shedlang.compiler.backends.tests.temporaryDirectory
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.tests.isSequence
import org.shedlang.compiler.types.BoolType
import org.shedlang.compiler.types.CodePointType
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.Type

private val environment = object: StackIrExecutionEnvironment {
    override fun evaluateExpression(node: ExpressionNode, type: Type): IrValue {
        val types = EMPTY_TYPES
        val instructions = loader(types = types).loadExpression(node)
        val exitCode = executeInstructions(instructions, moduleSet = ModuleSet(listOf()))

        return when (type) {
            BoolType ->
                when (exitCode) {
                    0L -> IrBool(false)
                    1L -> IrBool(true)
                    else -> throw UnsupportedOperationException()
                }

            CodePointType ->
                IrCodePoint(exitCode.toInt())

            IntType ->
                IrInt(exitCode.toBigInteger())

            else ->
                throw java.lang.UnsupportedOperationException("unsupported type: ${type.shortDescription}")
        }
    }

    private fun executeInstructions(
        instructions: PersistentList<Instruction>,
        moduleSet: ModuleSet
    ): Long {
        val image = loadModuleSet(moduleSet)

        val compiler = Compiler(image = image, moduleSet = moduleSet)
        val context = Compiler.Context()
        val llvmStatements = compiler.compileInstructions(instructions, context = context)
            .mapValue { llvmInstructions ->
                LlvmFunctionDefinition(
                    name = "main",
                    returnType = LlvmTypes.i64,
                    body = llvmInstructions + listOf(
                        LlvmReturn(LlvmTypes.i64, context.popTemporary())
                    )
                )
            }
            .toModuleStatements()

        val module = LlvmModule(llvmStatements)

        temporaryDirectory().use { temporaryDirectory ->
            val outputPath = temporaryDirectory.file.toPath().resolve("program.ll")
            outputPath.toFile().writeText(serialiseProgram(module))
            return executeLlvmInterpreter(outputPath).exitCode.toLong()
        }
    }
}

class LlvmCompilerTests: StackIrExecutionTests(environment)

class StackValueToLlvmOperandTests {
    @Test
    fun falseIsCompiledToZero() {
        val operand = stackValueToLlvmOperand(IrBool(false))

        assertThat(operand, isCompilationResult(isLlvmOperandInt(0)))
    }

    @Test
    fun trueIsCompiledToOne() {
        val operand = stackValueToLlvmOperand(IrBool(true))

        assertThat(operand, isCompilationResult(isLlvmOperandInt(1)))
    }

    @Test
    fun codePointIsCompiledToImmediateIntegerOperand() {
        val operand = stackValueToLlvmOperand(IrCodePoint(42))

        assertThat(operand, isCompilationResult(isLlvmOperandInt(42)))
    }

    @Test
    fun integerIsCompiledToImmediateIntegerOperand() {
        val operand = stackValueToLlvmOperand(IrInt(42))

        assertThat(operand, isCompilationResult(isLlvmOperandInt(42)))
    }

    @Test
    fun stringIsCompiledToPointerToGlobal() {
        val operand = stackValueToLlvmOperand(IrString("Hello"))

        assertThat(operand, isCompilationResult(
            isLlvmOperandPtrToInt(
                sourceType = equalTo(LlvmTypes.pointer(LlvmTypes.structure(listOf(
                    LlvmTypes.i64,
                    LlvmTypes.arrayType(5, LlvmTypes.i8)
                )))),
                value = isLlvmOperandGlobal("NAME_string"),
                targetType = equalTo(compiledValueType)
            ),
            moduleStatements = isSequence(
                isLlvmGlobalDefinition(
                    name = equalTo("NAME_string"),
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
            )
        ))
    }

    @Test
    fun unitIsCompiledToZero() {
        val operand = stackValueToLlvmOperand(IrUnit)

        assertThat(operand, isCompilationResult(isLlvmOperandInt(0)))
    }
}

private fun stackValueToLlvmOperand(value: IrValue): CompilationResult<LlvmOperand> {
    return stackValueToLlvmOperand(value, generateName = { name -> "NAME_$name"})
}

private fun <T> isCompilationResult(
    value: Matcher<T>,
    moduleStatements: Matcher<List<LlvmModuleStatement>> = isSequence()
): Matcher<CompilationResult<T>> {
    return allOf(
        has(CompilationResult<T>::value, value),
        has(CompilationResult<T>::moduleStatements, moduleStatements)
    )
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
): Matcher<LlvmModuleStatement> {
    return cast(allOf(
        has(LlvmGlobalDefinition::name, name),
        has(LlvmGlobalDefinition::type, type),
        has(LlvmGlobalDefinition::value, value),
        has(LlvmGlobalDefinition::isConstant, isConstant),
        has(LlvmGlobalDefinition::unnamedAddr, unnamedAddr)
    ))
}
