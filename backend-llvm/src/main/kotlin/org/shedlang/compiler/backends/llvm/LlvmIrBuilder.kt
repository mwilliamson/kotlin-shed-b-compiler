package org.shedlang.compiler.backends.llvm

import org.shedlang.compiler.ast.Identifier

internal class LlvmIrBuilder() {
    private var nextNameIndex = 1
    private var nextLabelIndex = 1

    internal fun generateName(prefix: Identifier) = generateName(prefix.value)

    internal fun generateName(prefix: String): String {
        return prefix + "_" + nextNameIndex++
    }

    internal fun generateLocal(prefix: Identifier): LlvmOperandLocal {
        return generateLocal(prefix.value)
    }

    internal fun generateLocal(prefix: String): LlvmOperandLocal {
        return LlvmOperandLocal(generateName(prefix))
    }

    internal fun createLlvmLabel(prefix: String): String {
        return "label_generated_" + prefix + "_" + nextLabelIndex++
    }
}
