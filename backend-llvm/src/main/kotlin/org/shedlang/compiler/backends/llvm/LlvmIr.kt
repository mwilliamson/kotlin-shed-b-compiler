package org.shedlang.compiler.backends.llvm

internal interface LlvmType {
    val byteSize: Int
    fun serialise(): String
}

internal data class LlvmTypeScalar(val name: String, override val byteSize: Int): LlvmType {
    override fun serialise(): String {
        return name
    }
}

internal data class LlvmTypePointer(val type: LlvmType): LlvmType {
    override val byteSize: Int
        get() = 8

    override fun serialise(): String {
        return "${type.serialise()} *"
    }
}

internal data class LlvmTypeArray(val size: Int, val elementType: LlvmType): LlvmType {
    override val byteSize: Int
        get() = elementType.byteSize * size

    override fun serialise(): String {
        return "[$size x ${elementType.serialise()}]"
    }
}

internal data class LlvmTypeFunction(
    val returnType: LlvmType,
    val parameterTypes: List<LlvmType>,
    val hasVarargs: Boolean
): LlvmType {
    override val byteSize: Int
        get() = throw UnsupportedOperationException()

    override fun serialise(): String {
        val parameterStrings = parameterTypes.map(LlvmType::serialise) + (if (hasVarargs) listOf("...") else listOf())
        val parametersString = parameterStrings.joinToString(", ")
        return "${returnType.serialise()} ($parametersString)"
    }
}

internal data class LlvmTypeStructure(val elementTypes: List<LlvmType>): LlvmType {
    override val byteSize: Int
        get() = elementTypes.sumBy { elementType -> elementType.byteSize }

    override fun serialise(): String {
        return "{${elementTypes.joinToString(", ") { elementType -> elementType.serialise() }}}"
    }
}

internal object LlvmTypes {
    val i1 = LlvmTypeScalar("i1", byteSize = 1)
    val i8 = LlvmTypeScalar("i8", byteSize = 1)
    val i32 = LlvmTypeScalar("i32", byteSize = 4)
    val i64 = LlvmTypeScalar("i64", byteSize = 8)
    val void = LlvmTypeScalar("void", byteSize = 0)

    fun pointer(type: LlvmType) = LlvmTypePointer(type = type)

    fun arrayType(size: Int, elementType: LlvmType) = LlvmTypeArray(size = size, elementType = elementType)

    fun function(
        returnType: LlvmType,
        parameterTypes: List<LlvmType>,
        hasVarargs: Boolean = false
    ): LlvmType = LlvmTypeFunction(
        returnType = returnType,
        parameterTypes = parameterTypes,
        hasVarargs = hasVarargs
    )

    fun structure(elementTypes: List<LlvmType>) = LlvmTypeStructure(elementTypes)
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

internal data class LlvmOperandInt(val value: Long): LlvmOperand {
    constructor(value: Int): this(value.toLong())

    override fun serialise(): String {
        return value.toString()
    }
}

internal data class LlvmOperandString(val value: String): LlvmOperand {
    override fun serialise(): String {
        return "c\"$value\""
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

internal data class LlvmOperandPtrToInt(
    val sourceType: LlvmType,
    val value: LlvmOperand,
    val targetType: LlvmType
): LlvmOperand {
    override fun serialise(): String {
        return "ptrtoint (${sourceType.serialise()} ${value.serialise()} to ${targetType.serialise()})"
    }
}

internal data class LlvmOperandArray(val elements: List<LlvmTypedOperand>): LlvmOperand {
    override fun serialise(): String {
        return "[${elements.joinToString(", ") { element -> element.serialise() }}]"
    }
}

internal data class LlvmOperandStructure(val elements: List<LlvmTypedOperand>): LlvmOperand {
    override fun serialise(): String {
        return "{${elements.joinToString(", ") { element -> element.serialise() }}}"
    }
}

internal data class LlvmTypedOperand(val type: LlvmType, val operand: LlvmOperand) {
    fun serialise(): String {
        return "${type.serialise()} ${operand.serialise()}"
    }
}

internal interface LlvmInstruction {
    fun serialise(): String
}

internal data class LlvmBitCast(
    val target: LlvmVariable,
    val sourceType: LlvmType,
    val value: LlvmOperand,
    val targetType: LlvmType
): LlvmInstruction {
    override fun serialise(): String {
        return "${target.serialise()} = bitcast ${sourceType.serialise()} ${value.serialise()} to ${targetType.serialise()}"
    }
}

internal data class LlvmIntToPtr(
    val target: LlvmVariable,
    val sourceType: LlvmType,
    val value: LlvmOperand,
    val targetType: LlvmType
): LlvmInstruction {
    override fun serialise(): String {
        return "${target.serialise()} = inttoptr ${sourceType.serialise()} ${value.serialise()} to ${targetType.serialise()}"
    }
}

internal data class LlvmPtrToInt(
    val target: LlvmVariable,
    val sourceType: LlvmType,
    val value: LlvmOperand,
    val targetType: LlvmType
): LlvmInstruction {
    override fun serialise(): String {
        return "${target.serialise()} = ptrtoint ${sourceType.serialise()} ${value.serialise()} to ${targetType.serialise()}"
    }
}

internal data class LlvmCall(
    val target: LlvmVariable?,
    val callingConvention: LlvmCallingConvention = LlvmCallingConvention.ccc,
    val returnType: LlvmType,
    val functionPointer: LlvmOperand,
    val arguments: List<LlvmTypedOperand>
): LlvmInstruction {
    override fun serialise(): String {
        val prefix = if (target == null) { "" } else { "${target.serialise()} = " }
        val argumentsString = arguments.joinToString(", ") { argument -> argument.serialise() }
        return "${prefix}call $callingConvention ${returnType.serialise()} ${functionPointer.serialise()}($argumentsString)"
    }
}

internal data class LlvmAdd(
    val target: LlvmVariable,
    val type: LlvmType,
    val left: LlvmOperand,
    val right: LlvmOperand
): LlvmInstruction {
    override fun serialise(): String {
        return "${target.serialise()} = add ${type.serialise()} ${left.serialise()}, ${right.serialise()}"
    }
}

internal data class LlvmAlloca(val target: LlvmVariable, val type: LlvmType): LlvmInstruction {
    override fun serialise(): String {
        return "${target.serialise()} = alloca ${type.serialise()}"
    }
}

internal data class LlvmAssign(
    val target: LlvmVariable,
    val value: LlvmOperand
): LlvmInstruction {
    override fun serialise(): String {
        return "${target.serialise()} = ${value.serialise()}"
    }

}

internal data class LlvmBr(
    val condition: LlvmOperand,
    val ifTrue: String,
    val ifFalse: String
): LlvmInstruction {
    override fun serialise(): String {
        return "br i1 ${condition.serialise()}, label %$ifTrue, label %$ifFalse"
    }
}

internal data class LlvmBrUnconditional(val label: String): LlvmInstruction {
    override fun serialise(): String {
        return "br label %$label"
    }
}

internal data class LlvmExtractValue(
    val target: LlvmVariable,
    val valueType: LlvmType,
    val value: LlvmOperand,
    val index: LlvmOperand
): LlvmInstruction {
    override fun serialise(): String {
        return "${target.serialise()} = extractvalue ${valueType.serialise()} ${value.serialise()}, ${index.serialise()}"
    }
}

internal data class LlvmGetElementPtr(
    val target: LlvmVariable,
    val pointerType: LlvmTypePointer,
    val pointer: LlvmOperand,
    val indices: List<LlvmIndex>
): LlvmInstruction {
    override fun serialise(): String {
        val indicesString = indices.joinToString("") {index -> ", ${index.serialise()}" }

        return "${target.serialise()} = getelementptr ${pointerType.type.serialise()}, ${pointerType.serialise()} ${pointer.serialise()}$indicesString"
    }
}

internal data class LlvmIcmp(
    val target: LlvmVariable,
    val conditionCode: ConditionCode,
    val type: LlvmType,
    val left: LlvmOperand,
    val right: LlvmOperand
): LlvmInstruction {
    enum class ConditionCode {
        EQ,
        NE,
        UGT,
        UGE,
        ULT,
        ULE,
        SGT,
        SGE,
        SLT,
        SLE
    }

    override fun serialise(): String {
        return "${target.serialise()} = icmp ${conditionCode.name.toLowerCase()} ${type.serialise()} ${left.serialise()}, ${right.serialise()}"
    }
}

internal data class LlvmIndex(val type: LlvmType, val value: LlvmOperand) {
    fun serialise(): String {
        return "${type.serialise()} ${value.serialise()}"
    }

    companion object {
        fun i64(value: Int): LlvmIndex {
            return LlvmIndex(LlvmTypes.i64, LlvmOperandInt(value))
        }

        fun i32(value: Int): LlvmIndex {
            return LlvmIndex(LlvmTypes.i32, LlvmOperandInt(value))
        }
    }
}

internal data class LlvmLabel(val name: String): LlvmInstruction {
    override fun serialise(): String {
        return "$name:"
    }
}

internal data class LlvmLoad(val target: LlvmVariable, val type: LlvmType, val pointer: LlvmOperand): LlvmInstruction {
    override fun serialise(): String {
        return "${target.serialise()} = load ${type.serialise()}, ${LlvmTypes.pointer(type).serialise()} ${pointer.serialise()}"
    }
}

internal data class LlvmMul(
    val target: LlvmVariable,
    val type: LlvmType,
    val left: LlvmOperand,
    val right: LlvmOperand
): LlvmInstruction {
    override fun serialise(): String {
        return "${target.serialise()} = mul ${type.serialise()} ${left.serialise()}, ${right.serialise()}"
    }
}

internal data class LlvmPhi(
    val target: LlvmVariable,
    val type: LlvmType,
    val pairs: List<LlvmPhiPair>
): LlvmInstruction {
    override fun serialise(): String {
        val pairsString = pairs.joinToString(", ") { pair -> "[${pair.value.serialise()}, %${pair.predecessorBasicBlockName}]" }
        return "${target.serialise()} = phi ${type.serialise()} $pairsString"
    }
}

internal data class LlvmPhiPair(val value: LlvmOperand, val predecessorBasicBlockName: String)

internal data class LlvmReturn(val type: LlvmType, val value: LlvmOperand): LlvmInstruction {
    override fun serialise(): String {
        return "ret ${type.serialise()} ${value.serialise()}"
    }
}

internal object LlvmReturnVoid: LlvmInstruction {
    override fun serialise(): String {
        return "ret void"
    }
}

internal data class LlvmStore(val type: LlvmType, val value: LlvmOperand, val pointer: LlvmOperand): LlvmInstruction {
    override fun serialise(): String {
        return "store ${type.serialise()} ${value.serialise()}, ${LlvmTypes.pointer(type).serialise()} ${pointer.serialise()}"
    }
}

internal data class LlvmSub(
    val target: LlvmVariable,
    val type: LlvmType,
    val left: LlvmOperand,
    val right: LlvmOperand
): LlvmInstruction {
    override fun serialise(): String {
        return "${target.serialise()} = sub ${type.serialise()} ${left.serialise()}, ${right.serialise()}"
    }
}

internal data class LlvmTrunc(
    val target: LlvmVariable,
    val sourceType: LlvmType,
    val operand: LlvmOperand,
    val targetType: LlvmType
): LlvmInstruction {
    override fun serialise(): String {
        return "${target.serialise()} = trunc ${sourceType.serialise()} ${operand.serialise()} to ${targetType.serialise()}"
    }
}

internal data class LlvmZext(
    val target: LlvmVariable,
    val sourceType: LlvmType,
    val operand: LlvmOperand,
    val targetType: LlvmType
): LlvmInstruction {
    override fun serialise(): String {
        return "${target.serialise()} = zext ${sourceType.serialise()} ${operand.serialise()} to ${targetType.serialise()}"
    }
}

internal enum class LlvmCallingConvention {
    ccc,
    fastcc
}

internal data class LlvmFunctionDefinition(
    val name: String,
    val returnType: LlvmType,
    val parameters: List<LlvmParameter>,
    val callingConvention: LlvmCallingConvention = LlvmCallingConvention.ccc,
    val body: List<LlvmInstruction>
): LlvmTopLevelEntity {
    override fun serialise(): String {
        val bodyString = body.joinToString("") { instruction ->
            // TODO: get rid of this hack
            if (instruction is LlvmLabel) { "" } else { "    " } + instruction.serialise()+ "\n"
        }
        val parametersString = parameters.joinToString(", ") { parameter -> parameter.serialise() }
        return "define $callingConvention ${returnType.serialise()} @$name($parametersString) {\n$bodyString}\n"
    }
}

internal data class LlvmFunctionDeclaration(
    val name: String,
    val returnType: LlvmType,
    val parameters: List<LlvmParameter>,
    val callingConvention: LlvmCallingConvention = LlvmCallingConvention.ccc,
    val hasVarargs: Boolean = false
): LlvmTopLevelEntity {
    fun type(): LlvmType {
        return LlvmTypes.function(
            returnType = returnType,
            parameterTypes = parameters.map { parameter -> parameter.type },
            hasVarargs = hasVarargs
        )
    }

    override fun serialise(): String {
        val parameterStrings = parameters.map { parameter ->
            parameter.serialise()
        } + if (hasVarargs) listOf("...") else listOf()
        val parametersString = parameterStrings.joinToString(", ")

        return "declare $callingConvention ${returnType.serialise()} @$name($parametersString)\n"
    }
}

internal data class LlvmParameter(
    val type: LlvmType,
    val name: String
) {
    fun serialise(): String {
        return "${type.serialise()} %$name"
    }
}

internal data class LlvmGlobalDefinition(
    val name: String,
    val type: LlvmType,
    val value: LlvmOperand,
    val isConstant: Boolean = false,
    val unnamedAddr: Boolean = false
): LlvmTopLevelEntity {
    override fun serialise(): String {
        val unnamedAddrString = if (unnamedAddr) "unnamed_addr " else ""
        val keyword = if (isConstant) "constant" else "global"
        return "@$name = $unnamedAddrString$keyword ${type.serialise()} ${value.serialise()}\n"
    }
}

internal data class LlvmModule(val body: List<LlvmTopLevelEntity>) {
    fun serialise(): String {
        return body.joinToString("") { entity -> entity.serialise() }
    }
}

interface LlvmTopLevelEntity {
    fun serialise(): String
}

internal fun isTerminator(instruction: LlvmInstruction): Boolean {
    return when (instruction) {
        is LlvmBr -> true
        is LlvmBrUnconditional -> true
        is LlvmReturn -> true
        is LlvmReturnVoid -> true
        else -> false
    }
}
