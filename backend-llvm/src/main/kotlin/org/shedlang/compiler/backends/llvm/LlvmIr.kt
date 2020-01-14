package org.shedlang.compiler.backends.llvm

internal interface LlvmType {
    fun serialise(): String
}

internal data class LlvmTypeScalar(val name: String): LlvmType {
    override fun serialise(): String {
        return name
    }
}

internal data class LlvmTypePointer(val type: LlvmType): LlvmType {
    override fun serialise(): String {
        return "${type.serialise()} *"
    }
}

internal data class LlvmTypeArray(val size: Int, val elementType: LlvmType): LlvmType {
    override fun serialise(): String {
        return "[$size x ${elementType.serialise()}]"
    }
}

internal data class LlvmTypeFunction(val returnType: LlvmType): LlvmType {
    override fun serialise(): String {
        return "${returnType.serialise()} ()"
    }
}

internal object LlvmTypes {
    val i8 = LlvmTypeScalar("i8")
    val i32 = LlvmTypeScalar("i32")
    val i64 = LlvmTypeScalar("i64")
    val void = LlvmTypeScalar("void")

    fun pointer(type: LlvmType) = LlvmTypePointer(type = type)

    fun arrayType(size: Int, elementType: LlvmType) = LlvmTypeArray(size = size, elementType = elementType)

    fun function(returnType: LlvmType): LlvmType = LlvmTypeFunction(returnType = returnType)
}

internal interface LlvmOperand {
    fun serialise(): String
}

internal interface LlvmVariable: LlvmOperand

internal object LlvmNullPointer: LlvmOperand {
    override fun serialise(): String {
        return "null"
    }
}

internal data class LlvmOperandInt(val value: Int): LlvmOperand {
    override fun serialise(): String {
        return value.toString()
    }
}

internal data class LlvmOperandGlobal(val name: String): LlvmVariable {
    override fun serialise(): String {
        return "@$name"
    }
}

internal data class LlvmOperandLocal(val name: String): LlvmVariable {
    override fun serialise(): String {
        return "%$name"
    }
}

internal data class LlvmTypedOperand(val type: LlvmType, val operand: LlvmOperand) {
    fun serialise(): String {
        return "${type.serialise()} ${operand.serialise()}"
    }
}

internal interface LlvmBasicBlock {
    fun serialise(): String
}

internal data class LlvmBitCast(
    val target: LlvmVariable,
    val sourceType: LlvmType,
    val value: LlvmOperand,
    val targetType: LlvmType
): LlvmBasicBlock {
    override fun serialise(): String {
        return "${target.serialise()} = bitcast ${sourceType.serialise()} ${value.serialise()} to ${targetType.serialise()}"
    }
}

internal data class LlvmIntToPtr(
    val target: LlvmVariable,
    val sourceType: LlvmType,
    val value: LlvmOperand,
    val targetType: LlvmType
): LlvmBasicBlock {
    override fun serialise(): String {
        return "${target.serialise()} = inttoptr ${sourceType.serialise()} ${value.serialise()} to ${targetType.serialise()}"
    }
}

internal data class LlvmPtrToInt(
    val target: LlvmVariable,
    val sourceType: LlvmType,
    val value: LlvmOperand,
    val targetType: LlvmType
): LlvmBasicBlock {
    override fun serialise(): String {
        return "${target.serialise()} = ptrtoint ${sourceType.serialise()} ${value.serialise()} to ${targetType.serialise()}"
    }
}

internal data class LlvmCall(
    val target: LlvmVariable?,
    val returnType: LlvmType,
    val functionPointer: LlvmOperand,
    val arguments: List<LlvmTypedOperand>
): LlvmBasicBlock {
    override fun serialise(): String {
        val prefix = if (target == null) { "" } else { "${target.serialise()} = " }
        val argumentsString = arguments.joinToString(", ") { argument -> argument.serialise() }
        return "${prefix}call ${returnType.serialise()} ${functionPointer.serialise()}($argumentsString)"
    }
}

internal data class LlvmAssign(
    val target: LlvmVariable,
    val value: LlvmOperand
): LlvmBasicBlock {
    override fun serialise(): String {
        return "${target.serialise()} = ${value.serialise()}"
    }

}

internal data class LlvmExtractValue(
    val target: LlvmVariable,
    val valueType: LlvmType,
    val value: LlvmOperand,
    val index: LlvmOperand
): LlvmBasicBlock {
    override fun serialise(): String {
        return "${target.serialise()} = extractvalue ${valueType.serialise()} ${value.serialise()}, ${index.serialise()}"
    }
}

internal data class LlvmGetElementPtr(
    val target: LlvmVariable,
    val type: LlvmType,
    val pointer: LlvmOperand,
    val indices: List<LlvmIndex>
): LlvmBasicBlock {
    override fun serialise(): String {
        val indicesString = indices.joinToString("") {index -> ", ${index.serialise()}" }

        return "${target.serialise()} = getelementptr ${type.serialise()}, ${LlvmTypes.pointer(type).serialise()} ${pointer.serialise()}$indicesString"
    }
}

internal data class LlvmIndex(val type: LlvmType, val value: LlvmOperand) {
    fun serialise(): String {
        return "${type.serialise()} ${value.serialise()}"
    }
}

internal data class LlvmLoad(val target: LlvmVariable, val type: LlvmType, val pointerType: LlvmType, val pointer: LlvmOperand): LlvmBasicBlock {
    override fun serialise(): String {
        return "${target.serialise()} = load ${type.serialise()}, ${pointerType.serialise()} ${pointer.serialise()}"
    }
}

internal data class LlvmReturn(val type: LlvmType, val value: LlvmOperand): LlvmBasicBlock {
    override fun serialise(): String {
        return "ret ${type.serialise()} ${value.serialise()}"
    }
}

internal object LlvmReturnVoid: LlvmBasicBlock {
    override fun serialise(): String {
        return "ret void"
    }
}

internal data class LlvmStore(val type: LlvmType, val value: LlvmOperand, val pointer: LlvmOperand): LlvmBasicBlock {
    override fun serialise(): String {
        return "store ${type.serialise()} ${value.serialise()}, ${LlvmTypes.pointer(type).serialise()} ${pointer.serialise()}"
    }
}

internal data class LlvmFunctionDefinition(
    val name: String,
    val returnType: LlvmType,
    val body: List<LlvmBasicBlock>
): LlvmModuleStatement {
    override fun serialise(): String {
        val bodyString = body.joinToString("") { block -> "    " + block.serialise()+ "\n" }
        return "define ${returnType.serialise()} @$name() {\n$bodyString}\n"
    }
}

internal data class LlvmGlobalDefinition(
    val name: String,
    val type: LlvmType,
    val value: LlvmOperand,
    val isConstant: Boolean = false,
    val unnamedAddr: Boolean = false
): LlvmModuleStatement {
    override fun serialise(): String {
        val unnamedAddrString = if (unnamedAddr) "unnamed_addr " else ""
        val keyword = if (isConstant) "constant" else "global"
        return "@$name = $unnamedAddrString$keyword ${type.serialise()} ${value.serialise()}\n"
    }
}

internal data class LlvmModule(val body: List<LlvmModuleStatement>) {
    fun serialise(): String {
        return body.joinToString("") { statement -> statement.serialise() }
    }
}

interface LlvmModuleStatement {
    fun serialise(): String
}
