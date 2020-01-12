package org.shedlang.compiler.backends.llvm.tests

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.backends.llvm.*
import org.shedlang.compiler.backends.tests.temporaryDirectory
import org.shedlang.compiler.backends.tests.testPrograms
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.typechecker.CompilerError
import org.shedlang.compiler.typechecker.SourceError
import org.shedlang.compiler.types.Type
import java.nio.file.Path

class ExecutionTests {
    private val disabledTests = setOf<String>(
        "BooleanOperations.shed",
        "cast",
        "Cons.shed",
        "ConstantField.shed",
        "dependencies",
        "FieldDestructuring.shed",
        "HelloWorld.shed",
        "localImports",
        "Matchers.shed",
        "moduleName",
        "NamedArguments.shed",
        "PolymorphicCons.shed",
        "PolymorphicForEach.shed",
        "PolymorphicIdentity.shed",
        "PolymorphicMap.shed",
        "RecursiveFactorial.shed",
        "RecursiveFibonacci.shed",
        "ShapeTypeInfo.shed",
        "stdlib",
        "symbols",
        "TailRec.shed",
        "Tuples.shed",
        "TypeAlias.shed",
        "usingStdlib",
        "Varargs.shed",
        "When.shed",
        "WhenElse.shed"
    )

    @TestFactory
    fun testProgram(): List<DynamicTest> {
        return testPrograms().filter { testProgram ->
            !disabledTests.contains(testProgram.name)
        }.map { testProgram -> DynamicTest.dynamicTest(testProgram.name) {
            try {
                temporaryDirectory().use { temporaryDirectory ->
                    val outputPath = temporaryDirectory.file.toPath().resolve("program.ll")
                    Compiler(testProgram.load()).compile(
                        target = outputPath,
                        mainModule = testProgram.mainModule
                    )
                    val result = org.shedlang.compiler.backends.tests.run(
                        listOf("lli", outputPath.toString()),
                        workingDirectory = temporaryDirectory.file
                    )
                    assertThat("stdout was:\n" + result.stdout + "\nstderr was:\n" + result.stderr, result, testProgram.expectedResult)
                }
            } catch (error: SourceError) {
                print(error.source.describe())
                throw error
            } catch (error: CompilerError) {
                print(error.source.describe())
                throw error
            }
        } }
    }
}

private class Compiler(private val moduleSet: ModuleSet) {
    private class Context {
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

    private val image = loadModuleSet(moduleSet)
    
    fun compile(target: Path, mainModule: List<Identifier>) {
        val defineMainModule = moduleDefine(mainModule)

        val mainModuleVariable = LlvmOperandLocal("mainModule")
        val mainFunctionUntypedVariable = LlvmOperandLocal("mainFunctionUntyped")
        val mainFunctionVariable = LlvmOperandLocal("mainFunction")
        val exitCodeVariable = LlvmOperandLocal("exitCode")
        val main = LlvmFunctionDefinition(
            returnType = LlvmTypes.i64,
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
                        sourceType = LlvmTypes.i64,
                        value = mainFunctionUntypedVariable,
                        targetType = LlvmTypes.pointer(LlvmTypes.function(
                            returnType = LlvmTypes.i64
                        ))
                    ),
                    LlvmCall(
                        target = exitCodeVariable,
                        returnType = LlvmTypes.i64,
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

        // TODO: handle malloc declaration properly
        val source = "declare i8* @malloc(i64)\n" + module.serialise()

        println(source.lines().mapIndexed { index, line ->
            (index + 1).toString().padStart(3) + " " + line
        }.joinToString("\n"))

        target.toFile().writeText(source)
    }

    private fun moduleDefine(moduleName: List<Identifier>): List<LlvmModuleStatement> {
        return listOf(
            LlvmGlobalDefinition(
                name = nameForModuleValue(moduleName),
                type = objectType,
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

    private fun compileInstructions(instructions: List<Instruction>, context: Context): CompilationResult<List<LlvmBasicBlock>> {
        return CompilationResult.flatten(instructions.map { instruction -> compileInstruction(instruction, context = context) })
    }

    private fun compileInstruction(instruction: Instruction, context: Context): CompilationResult<List<LlvmBasicBlock>> {
        when (instruction) {
            is DeclareFunction -> {
                val functionName = generateName("function")
                val functionPointerVariable = LlvmOperandLocal(generateName("functionPointer"))
                return compileInstructions(instruction.bodyInstructions, context = context)
                    .flatMapValue { instructions ->
                        val functionDefinition = LlvmFunctionDefinition(
                            name = functionName,
                            returnType = LlvmTypes.i64,
                            body = instructions
                        )

                        context.pushTemporary(functionPointerVariable)

                        val getVariableAddress = LlvmPtrToInt(
                            target = functionPointerVariable,
                            targetType = LlvmTypes.i64,
                            value = LlvmOperandGlobal(functionName),
                            sourceType = LlvmTypes.pointer(LlvmTypes.function(returnType = LlvmTypes.i64))
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
                            LlvmTypedOperand(LlvmTypes.i64, LlvmOperandInt(8 * instruction.exports.size))
                        )
                    ),
                    LlvmBitCast(
                        target = moduleVariable,
                        sourceType = LlvmTypes.pointer(LlvmTypes.i8),
                        value = moduleVariableUntyped,
                        targetType = objectType
                    ),
                    // TODO: don't assume exactly one export
                    LlvmGetElementPtr(
                        target = fieldVariable,
                        type = objectType.type,
                        pointer = moduleVariable,
                        indices = listOf(
                            LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0)),
                            LlvmIndex(LlvmTypes.i64, LlvmOperandInt(0))
                        )
                    ),
                    LlvmStore(
                        type = LlvmTypes.i64,
                        value = context.loadLocal(exportVariableId),
                        pointer = fieldVariable
                    ),
                    LlvmStore(
                        type = objectType,
                        value = moduleVariable,
                        pointer = operandForModuleValue(instruction.moduleName)
                    )
                ))
            }

            is PushValue -> {
                context.pushTemporary(generateOperand(instruction.value))

                return CompilationResult.of(listOf())
            }

            is Return -> {
                val returnVariable = context.popTemporary()

                return CompilationResult.of(listOf(
                    LlvmReturn(type = LlvmTypes.i64, value = returnVariable)
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

    private fun generateOperand(value: IrValue): LlvmOperand {
        if (value is IrInt) {
            return LlvmOperandInt(value.value.intValueExact())
        } else {
            throw UnsupportedOperationException(value.toString())
        }
    }

    private fun fieldAccess(receiver: LlvmOperand, fieldName: Identifier, receiverType: Type, target: LlvmVariable): List<LlvmBasicBlock> {
        val fieldPointerVariable = LlvmOperandLocal("fieldPointer")

        // TODO: calculate fieldIndex
        val fieldIndex = 0

        return listOf(
            LlvmGetElementPtr(
                target = fieldPointerVariable,
                type = objectType.type,
                pointer = receiver,
                indices = listOf(
                    LlvmIndex(LlvmTypes.i32, LlvmOperandInt(0)),
                    LlvmIndex(LlvmTypes.i32, LlvmOperandInt(fieldIndex))
                )
            ),
            LlvmLoad(
                target = target,
                type = LlvmTypes.i64,
                pointerType = LlvmTypes.pointer(LlvmTypes.i64),
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
                type = objectType,
                pointerType = LlvmTypes.pointer(objectType),
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

    private val objectType = LlvmTypes.pointer(LlvmTypes.arrayType(size = 0, elementType = LlvmTypes.i64))

    private class CompilationResult<T>(
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

    private fun CompilationResult<LlvmModuleStatement>.toModuleStatements(): List<LlvmModuleStatement> {
        return moduleStatements + listOf(value)
    }
}

private fun <T> MutableList<T>.pop() = removeAt(lastIndex)
