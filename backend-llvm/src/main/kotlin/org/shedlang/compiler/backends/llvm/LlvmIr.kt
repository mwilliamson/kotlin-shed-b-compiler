package org.shedlang.compiler.backends.llvm

internal data class LlvmType(val name: String) {
    fun serialise(): String {
        return name
    }
}

internal object LlvmTypes {
    val i32 = LlvmType("i32")
}

internal interface LlvmOperand {
    fun serialise(): String
}

internal data class LlvmOperandInt(val value: Int): LlvmOperand {
    override fun serialise(): String {
        return value.toString()
    }
}

internal interface LlvmBasicBlock {
    fun serialise(): String
}

internal data class LlvmReturn(val type: LlvmType, val value: LlvmOperand): LlvmBasicBlock {
    override fun serialise(): String {
        return "ret ${type.serialise()} ${value.serialise()}"
    }
}

internal data class LlvmFunctionDefinition(
    val name: String,
    val returnType: LlvmType,
    val body: List<LlvmBasicBlock>
) {
    fun serialise(): String {
        val bodyString = body.joinToString("") { block -> "    " + block.serialise()+ "\n" }
        return "define ${returnType.serialise()} @$name() {\n$bodyString}\n"
    }
}
