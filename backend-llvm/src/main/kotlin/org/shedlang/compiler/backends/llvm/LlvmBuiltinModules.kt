package org.shedlang.compiler.backends.llvm

import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.backends.ShedRuntime
import org.shedlang.compiler.types.FunctionType
import org.shedlang.compiler.types.TypeRegistry

internal class BuiltinModuleCompiler(
    private val moduleSet: ModuleSet,
    private val irBuilder: LlvmIrBuilder,
    private val closures: ClosureCompiler,
    private val libc: LibcCallCompiler,
    private val modules: ModuleValueCompiler,
    private val strings: StringCompiler
) {
    private val builtinModules = mapOf<ModuleName, (FunctionContext) -> FunctionContext>(
        listOf(Identifier("Core"), Identifier("Cast")) to ::compileCoreCast,
        listOf(Identifier("Core"), Identifier("Io")) to ::compileCoreIo,
        listOf(Identifier("Core"), Identifier("IntToString")) to ::compileCoreIntToString,
        listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("Process")) to ::compileStdlibPlatformProcess,
        listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("StringBuilder")) to ::compileStdlibPlatformStringBuilder,
        listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("Strings")) to ::compileStdlibPlatformStrings
    )

    internal fun isBuiltinModule(moduleName: ModuleName): Boolean {
        return builtinModules.containsKey(moduleName)
    }

    internal fun compileBuiltinModule(moduleName: ModuleName, context: FunctionContext): FunctionContext {
        return builtinModules.getValue(moduleName)(context)
    }

    private fun compileCoreCast(context: FunctionContext): FunctionContext {
        return compileCModule(
            moduleName = listOf(Identifier("Core"), Identifier("Cast")),
            functionNames = listOf(
                "cast"
            ),
            context = context
        )
    }

    private fun compileCoreIo(context: FunctionContext): FunctionContext {
        return compileCModule(
            moduleName = listOf(Identifier("Core"), Identifier("Io")),
            functionNames = listOf(
                "print"
            ),
            context = context
        )
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
        val moduleName = listOf(Identifier("Core"), Identifier("IntToString"))
        val functionName = ShedRuntime.functionSymbolName(moduleName, Identifier("intToString"))

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

        val intToStringClosure = irBuilder.generateLocal("intToString")

        return closures.compileCreate(
            target = intToStringClosure,
            functionName = functionName,
            freeVariables = listOf(),
            positionalParams = listOf(LlvmParameter(compiledValueType, "value")),
            namedParams = listOf(),
            compileBody = { bodyContext ->
                val instructions = strings.allocString(
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
                bodyContext
                    .addInstructions(instructions)
                    .addTopLevelEntities(intToStringFormatStringDefinition)
            },
            context = context
        ).addInstructions(
            modules.storeFields(
                moduleName = moduleName,
                exports = listOf(
                    Identifier("intToString") to intToStringClosure
                )
            )
        )
    }

    private fun compileStdlibPlatformProcess(context: FunctionContext): FunctionContext {
        return compileCModule(
            moduleName = listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("Process")),
            functionNames = listOf(
                "args"
            ),
            context = context
        )
    }

    private fun compileStdlibPlatformStringBuilder(context: FunctionContext): FunctionContext {
        return compileCModule(
            moduleName = listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("StringBuilder")),
            functionNames = listOf(
                "build",
                "write"
            ),
            context = context
        )
    }

    private fun compileStdlibPlatformStrings(context: FunctionContext): FunctionContext {
        return compileCModule(
            moduleName = listOf(Identifier("Stdlib"), Identifier("Platform"), Identifier("Strings")),
            functionNames = listOf(
                "dropLeftUnicodeScalars",
                "next",
                "replace",
                "slice",
                "substring",
                "unicodeScalarCount",
                "unicodeScalarToInt",
                "unicodeScalarToHexString",
                "unicodeScalarToString"
            ),
            context = context
        )
    }

    private fun compileCModule(
        moduleName: ModuleName,
        functionNames: List<String>,
        context: FunctionContext
    ): FunctionContext {
        val moduleType = moduleSet.moduleType(moduleName)!!
        val closures = mutableMapOf<String, LlvmOperand>()

        return functionNames.fold(context) { context2, functionName ->
            val closure = irBuilder.generateLocal(functionName)
            closures[functionName] = closure
            val functionType = moduleSet.typeRegistry.fieldType(moduleType, Identifier(functionName)) as FunctionType
            createClosureForCFunction(
                target = closure,
                functionName = ShedRuntime.functionSymbolName(moduleName, Identifier(functionName)),
                parameterCount = functionType.positionalParameters.size + functionType.namedParameters.size,
                context = context2
            )
        }
            .addInstructions(modules.storeFields(
                moduleName = moduleName,
                exports = functionNames.map { functionName ->
                    Identifier(functionName) to closures.getValue(functionName)
                }
            ))
    }

    private fun createClosureForCFunction(
        target: LlvmVariable,
        functionName: String,
        parameterCount: Int,
        context: FunctionContext
    ): FunctionContext {
        val parameterTypes = (0 until parameterCount).map { compiledValueType }
        return context
            .let {
                closures.createClosure(
                    target = target,
                    functionName = functionName,
                    parameterTypes = parameterTypes,
                    freeVariables = listOf(),
                    context = it
                )
            }
            .addTopLevelEntities(LlvmFunctionDeclaration(
                name = functionName,
                callingConvention = LlvmCallingConvention.ccc,
                returnType = compiledValueType,
                parameters = listOf(
                    LlvmParameter(compiledClosureEnvironmentPointerType, "environment")
                ) + parameterTypes.mapIndexed { parameterIndex, parameterType ->
                    LlvmParameter(parameterType, "arg_$parameterIndex")
                }
            ))
    }
}
