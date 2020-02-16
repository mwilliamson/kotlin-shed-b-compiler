package org.shedlang.compiler.backends.llvm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName

internal class BuiltinModuleCompiler(
    private val irBuilder: LlvmIrBuilder,
    private val closures: ClosureCompiler,
    private val libc: LibcCallCompiler,
    private val modules: ModuleValueCompiler,
    private val strings: StringCompiler
) {
    private val builtinModules = mapOf<ModuleName, (FunctionContext) -> FunctionContext>(
        listOf(Identifier("Core"), Identifier("Io")) to ::compileCoreIo
    )

    internal fun isBuiltinModule(moduleName: ModuleName): Boolean {
        return builtinModules.containsKey(moduleName)
    }

    internal fun compileBuiltinModule(moduleName: ModuleName, context: FunctionContext): FunctionContext {
        return builtinModules.getValue(moduleName)(context)
    }

    private fun compileCoreIo(context: FunctionContext): FunctionContext {
        // TODO: allocate of global names
        val functionName = "Core_Io_print"

        val print = LlvmFunctionDefinition(
            name = functionName,
            parameters = listOf(
                LlvmParameter(compiledClosureEnvironmentPointerType, "environment"),
                LlvmParameter(compiledValueType, "value")
            ),
            returnType = compiledValueType,
            body = print(LlvmOperandLocal("value")) + listOf(
                LlvmReturn(type = compiledValueType, value = compiledUnitValue)
            )
        )

        val printClosure = irBuilder.generateLocal("print")

        return closures.createClosure(
            target = printClosure,
            functionName = functionName,
            parameterTypes = listOf(compiledValueType),
            freeVariables = listOf(),
            context = context
        )
            .addInstructions(
                modules.storeFields(
                    moduleName = listOf(Identifier("Core"), Identifier("Io")),
                    exports = listOf(
                        Identifier("print") to printClosure
                    )
                )
            )
            .addTopLevelEntities(print)
    }

    internal fun print(stringValue: LlvmOperand): List<LlvmInstruction> {
        val stdoutFd = 1
        val string = LlvmOperandLocal("string")
        val size = LlvmOperandLocal("size")
        val dataPointer = LlvmOperandLocal("dataPointer")
        return listOf(
            strings.rawValueToString(target = string, source = stringValue)
        ) + strings.stringSize(
            target = size,
            source = string
        ) + listOf(
            strings.stringDataStart(
                target = dataPointer,
                source = string
            ),
            libc.write(
                fd = LlvmOperandInt(stdoutFd),
                buf = dataPointer,
                count = size
            )
        )
    }
}
