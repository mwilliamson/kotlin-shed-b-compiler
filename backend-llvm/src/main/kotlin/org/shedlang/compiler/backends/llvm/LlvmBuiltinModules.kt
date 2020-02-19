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
        listOf(Identifier("Core"), Identifier("Io")) to ::compileCoreIo,
        listOf(Identifier("Core"), Identifier("IntToString")) to ::compileCoreIntToString
    )

    internal fun isBuiltinModule(moduleName: ModuleName): Boolean {
        return builtinModules.containsKey(moduleName)
    }

    internal fun compileBuiltinModule(moduleName: ModuleName, context: FunctionContext): FunctionContext {
        return builtinModules.getValue(moduleName)(context)
    }

    private fun compileCoreIo(context: FunctionContext): FunctionContext {
        // TODO: allocation of global names
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

    private fun compileCoreIntToString(context: FunctionContext): FunctionContext {
        // TODO: allocation of global names
        val functionName = "Core_Io_intToString"

        val format = LlvmOperandLocal("format")
        val string = LlvmOperandLocal("string")
        val stringDataStart = LlvmOperandLocal("stringDataStart")
        val stringLength = LlvmOperandLocal("stringLength")
        val stringLengthExtended = LlvmOperandLocal("stringLengthExtended")
        val result = LlvmOperandLocal("result")

        val intToStringFormatStringDefinition = LlvmGlobalDefinition(
            name = irBuilder.generateName("intToStringFormat"),
            type = LlvmTypes.arrayType(4, LlvmTypes.i8),
            value = LlvmOperandString("%ld\\00"),
            unnamedAddr = true,
            isConstant = true
        )

        val maxLength = 21
        val intToString = LlvmFunctionDefinition(
            name = functionName,
            parameters = listOf(
                LlvmParameter(compiledClosureEnvironmentPointerType, "environment"),
                LlvmParameter(compiledValueType, "value")
            ),
            returnType = compiledValueType,
            body = strings.allocString(
                target = string,
                dataSize = LlvmOperandInt(maxLength)
            ) + listOf(
                strings.stringDataStart(target = stringDataStart, source = string),
                LlvmGetElementPtr(
                    target = format,
                    pointerType = LlvmTypes.pointer(intToStringFormatStringDefinition.type),
                    pointer = LlvmOperandGlobal(intToStringFormatStringDefinition.name),
                    indices = listOf(
                        LlvmIndex.i64(0),
                        LlvmIndex.i64(0)
                    )
                ),
                libc.snprintf(
                    target = stringLength,
                    str = stringDataStart,
                    size = LlvmOperandInt(maxLength),
                    format = format,
                    args = listOf(
                        LlvmTypedOperand(compiledIntType, LlvmOperandLocal("value"))
                    )
                ),
                LlvmZext(
                    target = stringLengthExtended,
                    sourceType = CTypes.int,
                    operand = stringLength,
                    targetType = compiledStringLengthType
                )
            ) + strings.storeStringDataSize(
                string = string,
                size = stringLengthExtended
            ) + listOf(
                LlvmPtrToInt(
                    target = result,
                    sourceType = compiledStringType(0),
                    value = string,
                    targetType = compiledValueType
                ),
                LlvmReturn(type = compiledValueType, value = result)
            )
        )

        val intToStringClosure = irBuilder.generateLocal("intToString")

        return closures.createClosure(
            target = intToStringClosure,
            functionName = functionName,
            parameterTypes = listOf(compiledValueType),
            freeVariables = listOf(),
            context = context
        )
            .addInstructions(
                modules.storeFields(
                    moduleName = listOf(Identifier("Core"), Identifier("IntToString")),
                    exports = listOf(
                        Identifier("intToString") to intToStringClosure
                    )
                )
            )
            .addTopLevelEntities(intToString, intToStringFormatStringDefinition)
    }
}