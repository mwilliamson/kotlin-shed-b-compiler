package org.shedlang.compiler.backends.llvm

import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName

internal class ModuleValueCompiler(
    private val irBuilder: LlvmIrBuilder,
    private val moduleSet: ModuleSet
) {
    internal fun defineModuleValue(moduleName: ModuleName): LlvmGlobalDefinition {
        return LlvmGlobalDefinition(
            name = nameForModuleValue(moduleName),
            type = compiledModuleType(moduleName).type,
            value = LlvmOperandArray(
                (0 until moduleSize(moduleName)).map {
                    LlvmTypedOperand(compiledValueType, LlvmOperandInt(0))
                }
            )
        )
    }

    internal fun load(target: LlvmVariable, moduleName: ModuleName): LlvmBitCast {
        return LlvmBitCast(
            target = target,
            sourceType = compiledObjectType(moduleSize(moduleName)),
            value = operandForModuleValue(moduleName),
            targetType = compiledObjectType()
        )
    }

    internal fun loadRaw(target: LlvmVariable, moduleName: ModuleName): LlvmInstruction {
        return LlvmPtrToInt(
            target = target,
            sourceType = compiledModuleType(moduleName),
            value = operandForModuleValue(moduleName),
            targetType = compiledValueType
        )
    }

    internal fun storeFields(moduleName: ModuleName, exports: List<Pair<Identifier, LlvmOperand>>): List<LlvmInstruction> {
        val fieldPointerVariable = LlvmOperandLocal(irBuilder.generateName("fieldPointer"))
        val moduleType = moduleType(moduleName)

        return exports.flatMap { (exportName, exportValue) ->
            listOf(
                LlvmGetElementPtr(
                    target = fieldPointerVariable,
                    pointerType = compiledObjectType(moduleSize(moduleName)),
                    pointer = operandForModuleValue(moduleName),
                    indices = listOf(
                        LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0)),
                        LlvmIndex(LlvmTypes.i64, LlvmOperandInt(fieldIndex(moduleType, exportName)))
                    )
                ),
                LlvmStore(
                    type = compiledValueType,
                    value = exportValue,
                    pointer = fieldPointerVariable
                )
            )
        }
    }

    private fun operandForModuleValue(moduleName: ModuleName): LlvmVariable {
        return LlvmOperandGlobal(nameForModuleValue(moduleName))
    }

    private fun nameForModuleValue(moduleName: ModuleName): String {
        return "shed__module_value__${serialiseModuleName(moduleName)}"
    }

    private fun serialiseModuleName(moduleName: ModuleName) =
        moduleName.joinToString("_") { part -> part.value }

    private fun compiledModuleType(moduleName: ModuleName) =
        compiledObjectType(moduleSize(moduleName))

    private fun moduleSize(moduleName: ModuleName) =
        moduleType(moduleName).fields.size

    private fun moduleType(moduleName: ModuleName) =
        moduleSet.moduleType(moduleName)!!
}
