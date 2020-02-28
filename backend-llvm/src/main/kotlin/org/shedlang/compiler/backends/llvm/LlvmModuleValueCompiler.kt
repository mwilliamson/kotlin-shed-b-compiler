package org.shedlang.compiler.backends.llvm

import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName

internal class ModuleValueCompiler(
    private val irBuilder: LlvmIrBuilder,
    private val moduleSet: ModuleSet
) {
    internal fun defineModuleValue(moduleName: ModuleName): LlvmGlobalDefinition {
        val compiledModuleType = compiledModuleType(moduleName)
        return LlvmGlobalDefinition(
            name = nameForModuleValue(moduleName),
            type = compiledModuleType.llvmType(),
            value = LlvmOperandStructure(
                compiledModuleType.llvmType().elementTypes.map { elementType ->
                    LlvmTypedOperand(elementType, LlvmOperandInt(0))
                }
            )
        )
    }

    internal fun modulePointer(moduleName: ModuleName): LlvmOperand {
        return operandForModuleValue(moduleName)
    }

    internal fun loadRaw(target: LlvmVariable, moduleName: ModuleName): LlvmInstruction {
        return LlvmPtrToInt(
            target = target,
            sourceType = compiledModuleType(moduleName).llvmPointerType(),
            value = operandForModuleValue(moduleName),
            targetType = compiledValueType
        )
    }

    internal fun storeFields(moduleName: ModuleName, exports: List<Pair<Identifier, LlvmOperand>>): List<LlvmInstruction> {
        val compiledObjectType = compiledType(moduleType(moduleName))

        return exports.flatMap { (exportName, exportValue) ->
            val fieldPointerVariable = LlvmOperandLocal(irBuilder.generateName("fieldPointer"))
            listOf(
                compiledObjectType.getFieldPointer(
                    target = fieldPointerVariable,
                    receiver = operandForModuleValue(moduleName),
                    fieldName = exportName
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
        compiledType(objectType = moduleType(moduleName))

    private fun moduleType(moduleName: ModuleName) =
        moduleSet.moduleType(moduleName)!!
}
