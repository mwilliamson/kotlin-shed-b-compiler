package org.shedlang.compiler.backends.llvm.tests

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.backends.llvm.*
import org.shedlang.compiler.stackir.IrBool
import org.shedlang.compiler.stackir.IrInt
import org.shedlang.compiler.stackir.IrUnit
import org.shedlang.compiler.tests.isSequence

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
    fun integerIsCompiledToImmediateIntegerOperand() {
        val operand = stackValueToLlvmOperand(IrInt(42))

        assertThat(operand, isCompilationResult(isLlvmOperandInt(42)))
    }

    @Test
    fun unitIsCompiledToZero() {
        val operand = stackValueToLlvmOperand(IrUnit)

        assertThat(operand, isCompilationResult(isLlvmOperandInt(0)))
    }
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

private fun isLlvmOperandInt(value: Int): Matcher<LlvmOperand> {
    return cast(has(LlvmOperandInt::value, equalTo(value)))
}
