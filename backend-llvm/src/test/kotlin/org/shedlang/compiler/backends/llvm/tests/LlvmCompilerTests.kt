package org.shedlang.compiler.backends.llvm.tests

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.backends.llvm.LlvmOperand
import org.shedlang.compiler.backends.llvm.LlvmOperandInt
import org.shedlang.compiler.backends.llvm.stackValueToLlvmOperand
import org.shedlang.compiler.stackir.IrBool
import org.shedlang.compiler.stackir.IrInt
import org.shedlang.compiler.stackir.IrUnit

class StackValueToLlvmOperandTests {
    @Test
    fun falseIsCompiledToZero() {
        assertThat(stackValueToLlvmOperand(IrBool(false)), isLlvmOperandInt(0))
    }

    @Test
    fun trueIsCompiledToOne() {
        assertThat(stackValueToLlvmOperand(IrBool(true)), isLlvmOperandInt(1))
    }

    @Test
    fun integerIsCompiledToImmediateIntegerOperand() {
        assertThat(stackValueToLlvmOperand(IrInt(42)), isLlvmOperandInt(42))
    }

    @Test
    fun unitIsCompiledToZero() {
        assertThat(stackValueToLlvmOperand(IrUnit), isLlvmOperandInt(0))
    }
}

private fun isLlvmOperandInt(value: Int): Matcher<LlvmOperand> {
    return cast(has(LlvmOperandInt::value, equalTo(value)))
}
