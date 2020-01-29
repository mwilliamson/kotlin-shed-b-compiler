package org.shedlang.compiler.backends.llvm

import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.types.Type
import java.nio.file.Path

internal class Compiler(private val image: Image, private val moduleSet: ModuleSet) {
    internal class Context {
        fun pushTemporary(operand: LlvmOperand) {
            stack.add(operand)
        }

        fun popTemporary(): LlvmOperand {
            return stack.pop()
        }

        fun storeLocal(variableId: Int, operand: LlvmOperand) {
            locals.put(variableId, operand)
        }

        fun loadLocal(variableId: Int): LlvmOperand {
            return locals.getValue(variableId)
        }

        private val stack: MutableList<LlvmOperand> = mutableListOf()
        private val locals: MutableMap<Int, LlvmOperand> = mutableMapOf()
    }

    fun compile(target: Path, mainModule: List<Identifier>) {
        val defineMainModule = moduleDefine(mainModule)

        val mainModuleVariable = LlvmOperandLocal("mainModule")
        val mainFunctionUntypedVariable = LlvmOperandLocal("mainFunctionUntyped")
        val mainFunctionVariable = LlvmOperandLocal("mainFunction")
        val exitCodeVariable = LlvmOperandLocal("exitCode")
        val main = LlvmFunctionDefinition(
            returnType = compiledValueType,
            name = "main",
            body = listOf(
                importModule(mainModule, target = mainModuleVariable),
                fieldAccess(
                    mainModuleVariable,
                    Identifier("main"),
                    receiverType = moduleSet.module(mainModule)!!.type,
                    target = mainFunctionUntypedVariable
                ),
                listOf(
                    LlvmIntToPtr(
                        target = mainFunctionVariable,
                        sourceType = compiledValueType,
                        value = mainFunctionUntypedVariable,
                        targetType = LlvmTypes.pointer(LlvmTypes.function(
                            returnType = compiledValueType
                        ))
                    ),
                    LlvmCall(
                        target = exitCodeVariable,
                        returnType = compiledValueType,
                        functionPointer = mainFunctionVariable,
                        arguments = listOf()
                    ),
                    LlvmReturn(type = LlvmTypes.i64, value = exitCodeVariable)
                )
            ).flatten()
        )

        val module = LlvmModule(
            listOf(
                defineMainModule,
                listOf(main)
            ).flatten()
        )

        val source = serialiseProgram(module)

        println(source.lines().mapIndexed { index, line ->
            (index + 1).toString().padStart(3) + " " + line
        }.joinToString("\n"))

        target.toFile().writeText(source)
    }

    private fun moduleDefine(moduleName: List<Identifier>): List<LlvmModuleStatement> {
        return listOf(
            LlvmGlobalDefinition(
                name = nameForModuleValue(moduleName),
                type = compiledObjectType,
                value = LlvmNullPointer
            )
        ) + moduleInit(moduleName)
    }

    private fun moduleInit(moduleName: List<Identifier>): List<LlvmModuleStatement> {
        return compileInstructions(image.moduleInitialisation(moduleName), context = Context()).mapValue<LlvmModuleStatement> { instructions ->
            LlvmFunctionDefinition(
                name = nameForModuleInit(moduleName),
                returnType = LlvmTypes.void,
                body = instructions + listOf(LlvmReturnVoid)
            )
        }.toModuleStatements()
    }

    internal fun compileInstructions(instructions: List<Instruction>, context: Context): CompilationResult<List<LlvmBasicBlock>> {
        return CompilationResult.flatten(instructions.map { instruction -> compileInstruction(instruction, context = context) })
    }

    private fun compileInstruction(instruction: Instruction, context: Context): CompilationResult<List<LlvmBasicBlock>> {
        when (instruction) {
            is BoolNot -> {
                val operand = context.popTemporary()
                val booleanResult = LlvmOperandLocal(generateName("not_i1"))
                val fullResult = LlvmOperandLocal(generateName("not"))

                context.pushTemporary(fullResult)

                return CompilationResult.of(listOf(
                    LlvmIcmp(
                        target = booleanResult,
                        conditionCode = LlvmIcmp.ConditionCode.EQ,
                        type = compiledBoolType,
                        left = operand,
                        right = LlvmOperandInt(0)
                    ),
                    LlvmZext(
                        target = fullResult,
                        sourceType = LlvmTypes.i1,
                        operand = booleanResult,
                        targetType = compiledBoolType
                    )
                ))
            }

            is DeclareFunction -> {
                val functionName = generateName("function")
                val functionPointerVariable = LlvmOperandLocal(generateName("functionPointer"))
                return compileInstructions(instruction.bodyInstructions, context = context)
                    .flatMapValue { instructions ->
                        val functionDefinition = LlvmFunctionDefinition(
                            name = functionName,
                            returnType = compiledValueType,
                            body = instructions
                        )

                        context.pushTemporary(functionPointerVariable)

                        val getVariableAddress = LlvmPtrToInt(
                            target = functionPointerVariable,
                            targetType = compiledValueType,
                            value = LlvmOperandGlobal(functionName),
                            sourceType = LlvmTypes.pointer(LlvmTypes.function(returnType = compiledValueType))
                        )
                        CompilationResult.of(listOf<LlvmBasicBlock>(getVariableAddress))
                            .addModuleStatements(listOf(functionDefinition))
                    }
            }

            is Exit -> {
                return CompilationResult.of(listOf())
            }

            is LocalStore -> {
                val operand = context.popTemporary()
                context.storeLocal(instruction.variableId, operand)
                return CompilationResult.of(listOf())
            }

            is ModuleStore -> {
                val moduleVariableUntyped = LlvmOperandLocal(generateName("moduleUntyped"))
                val moduleVariable = LlvmOperandLocal(generateName("module"))
                val fieldVariable = LlvmOperandLocal(generateName("field"))
                val (exportName, exportVariableId) = instruction.exports.single()
                return CompilationResult.of(listOf(
                    LlvmCall(
                        target = moduleVariableUntyped,
                        returnType = LlvmTypes.pointer(LlvmTypes.i8),
                        functionPointer = LlvmOperandGlobal("malloc"),
                        arguments = listOf(
                            LlvmTypedOperand(compiledValueType, LlvmOperandInt(8 * instruction.exports.size))
                        )
                    ),
                    LlvmBitCast(
                        target = moduleVariable,
                        sourceType = LlvmTypes.pointer(LlvmTypes.i8),
                        value = moduleVariableUntyped,
                        targetType = compiledObjectType
                    ),
                    // TODO: don't assume exactly one export
                    LlvmGetElementPtr(
                        target = fieldVariable,
                        type = compiledObjectType.type,
                        pointer = moduleVariable,
                        indices = listOf(
                            LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0)),
                            LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0))
                        )
                    ),
                    LlvmStore(
                        type = compiledValueType,
                        value = context.loadLocal(exportVariableId),
                        pointer = fieldVariable
                    ),
                    LlvmStore(
                        type = compiledObjectType,
                        value = moduleVariable,
                        pointer = operandForModuleValue(instruction.moduleName)
                    )
                ))
            }

            is PushValue -> {
                return stackValueToLlvmOperand(instruction.value, generateName = ::generateName).mapValue { operand ->
                    context.pushTemporary(operand)
                    listOf<LlvmBasicBlock>()
                }
            }

            is Return -> {
                val returnVariable = context.popTemporary()

                return CompilationResult.of(listOf(
                    LlvmReturn(type = compiledValueType, value = returnVariable)
                ))
            }

            else -> {
                throw UnsupportedOperationException(instruction.toString())
            }
        }
    }

    private fun variableForLocal(variableId: Int): LlvmVariable {
        return LlvmOperandLocal("local_$variableId")
    }

    private fun fieldAccess(receiver: LlvmOperand, fieldName: Identifier, receiverType: Type, target: LlvmVariable): List<LlvmBasicBlock> {
        val fieldPointerVariable = LlvmOperandLocal("fieldPointer")

        // TODO: calculate fieldIndex
        val fieldIndex = 0

        return listOf(
            LlvmGetElementPtr(
                target = fieldPointerVariable,
                type = compiledObjectType.type,
                pointer = receiver,
                indices = listOf(
                    LlvmIndex(LlvmTypes.i32, LlvmOperandInt(0)),
                    LlvmIndex(LlvmTypes.i32, LlvmOperandInt(fieldIndex))
                )
            ),
            LlvmLoad(
                target = target,
                type = compiledValueType,
                pointerType = LlvmTypes.pointer(compiledValueType),
                pointer = fieldPointerVariable
            )
        )
    }

    private fun importModule(moduleName: List<Identifier>, target: LlvmVariable): List<LlvmBasicBlock> {
        return listOf(
            LlvmCall(
                target = null,
                returnType = LlvmTypes.void,
                functionPointer = operandForModuleInit(moduleName),
                arguments = listOf()
            ),
            LlvmLoad(
                target = target,
                type = compiledObjectType,
                pointerType = LlvmTypes.pointer(compiledObjectType),
                pointer = operandForModuleValue(moduleName)
            )
        )
    }

    private fun operandForModuleInit(moduleName: List<Identifier>): LlvmOperand {
        return LlvmOperandGlobal(nameForModuleInit(moduleName))
    }

    private fun operandForModuleValue(moduleName: List<Identifier>): LlvmVariable {
        return LlvmOperandGlobal(nameForModuleValue(moduleName))
    }

    private fun nameForModuleInit(moduleName: List<Identifier>): String {
        return "shed__module_init__${serialiseModuleName(moduleName)}"
    }

    private fun nameForModuleValue(moduleName: List<Identifier>): String {
        return "shed__module_value__${serialiseModuleName(moduleName)}"
    }

    private fun serialiseModuleName(moduleName: List<Identifier>) =
        moduleName.joinToString("_") { part -> part.value }

    var nextNameIndex = 1

    private fun generateName(prefix: String): String {
        return prefix + "_" + nextNameIndex++
    }
}

private fun <T> MutableList<T>.pop() = removeAt(lastIndex)

internal val compiledValueType = LlvmTypes.i64
internal val compiledBoolType = compiledValueType
private val compiledObjectType = LlvmTypes.pointer(LlvmTypes.arrayType(size = 0, elementType = compiledValueType))

internal fun stackValueToLlvmOperand(
    value: IrValue,
    generateName: (String) -> String
): CompilationResult<LlvmOperand> {
    return when (value) {
        is IrBool ->
            CompilationResult.of(LlvmOperandInt(if (value.value) 1 else 0))

        is IrCodePoint ->
            CompilationResult.of(LlvmOperandInt(value.value))

        is IrInt ->
            CompilationResult.of(LlvmOperandInt(value.value.intValueExact()))

        is IrString -> {
            val globalName = generateName("string")
            val bytes = value.value.toByteArray(Charsets.UTF_8)

            val stringDataType = LlvmTypes.arrayType(bytes.size, LlvmTypes.i8)
            val stringValueType = LlvmTypes.structure(listOf(
                LlvmTypes.i64,
                stringDataType
            ))
            val operand: LlvmOperand = LlvmOperandPtrToInt(
                sourceType = LlvmTypes.pointer(stringValueType),
                value = LlvmOperandGlobal(globalName),
                targetType = compiledValueType
            )

            val stringDefinition = LlvmGlobalDefinition(
                name = globalName,
                type = stringValueType,
                value = LlvmOperandStructure(listOf(
                    LlvmTypedOperand(LlvmTypes.i64, LlvmOperandInt(bytes.size)),
                    LlvmTypedOperand(
                        stringDataType,
                        LlvmOperandArray(bytes.map { byte ->
                            LlvmTypedOperand(LlvmTypes.i8, LlvmOperandInt(byte.toInt()))
                        })
                    )
                )),
                unnamedAddr = true,
                isConstant = true
            )

            CompilationResult.of(operand).addModuleStatements(listOf(stringDefinition))
        }

        is IrUnit ->
            CompilationResult.of(LlvmOperandInt(0))

        else ->
            throw UnsupportedOperationException(value.toString())
    }
}

internal class CompilationResult<out T>(
    val value: T,
    val moduleStatements: List<LlvmModuleStatement>
) {
    fun <R> mapValue(func: (T) -> R): CompilationResult<R> {
        return CompilationResult(
            value = func(value),
            moduleStatements = moduleStatements
        )
    }

    fun <R> flatMapValue(func: (T) -> CompilationResult<R>): CompilationResult<R> {
        val result = func(value)
        return CompilationResult(
            value = result.value,
            moduleStatements = moduleStatements + result.moduleStatements
        )
    }

    fun addModuleStatements(moduleStatements: List<LlvmModuleStatement>): CompilationResult<T> {
        return CompilationResult(
            value = value,
            moduleStatements = this.moduleStatements + moduleStatements
        )
    }

    companion object {
        val EMPTY = CompilationResult<List<Nothing>>(
            value = listOf(),
            moduleStatements = listOf()
        )

        fun <T> of(value: T): CompilationResult<T> {
            return CompilationResult(
                value = value,
                moduleStatements = listOf()
            )
        }

        fun <T> flatten(results: List<CompilationResult<List<T>>>): CompilationResult<List<T>> {
            val value = results.flatMap { result -> result.value }
            val moduleStatements = results.flatMap { result -> result.moduleStatements }
            return CompilationResult(
                value = value,
                moduleStatements = moduleStatements
            )
        }
    }
}

internal fun CompilationResult<LlvmModuleStatement>.toModuleStatements(): List<LlvmModuleStatement> {
    return moduleStatements + listOf(value)
}

internal fun serialiseProgram(module: LlvmModule): String {
    // TODO: handle malloc declaration properly
    return "declare i8* @malloc(i64)\n" + module.serialise()
}
