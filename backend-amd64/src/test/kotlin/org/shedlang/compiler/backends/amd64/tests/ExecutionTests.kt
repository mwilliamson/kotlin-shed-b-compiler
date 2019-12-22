package org.shedlang.compiler.backends.amd64.tests

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.backends.amd64.*
import org.shedlang.compiler.backends.tests.temporaryDirectory
import org.shedlang.compiler.backends.tests.testPrograms
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.stackir.Instruction
import org.shedlang.compiler.typechecker.CompilerError
import org.shedlang.compiler.typechecker.SourceError
import org.shedlang.compiler.types.FunctionType
import org.shedlang.compiler.types.ModuleType
import org.shedlang.compiler.types.UnitType
import java.nio.file.Path

class ExecutionTests {
    private val disabledTests = setOf<String>(
        "BooleanOperations.shed",
        "cast",
        "Cons.shed",
        "ConstantField.shed",
        "dependencies",
        "FieldDestructuring.shed",
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
                    val outputPath = temporaryDirectory.file.toPath().resolve("program")
                    Compiler(testProgram.load()).compile(
                        target = outputPath,
                        mainModule = testProgram.mainModule
                    )
                    val result = org.shedlang.compiler.backends.tests.run(
                        listOf(outputPath.toString()),
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

private const val VALUE_SIZE = 8

private class CompilationResult<T>(
    val value: T,
    val text: List<Line>,
    val bss: List<Line>
) {
    fun <R> mapInstructions(func: (T) -> R): CompilationResult<R> {
        return CompilationResult(
            value = func(value),
            text = text,
            bss = bss
        )
    }

    fun addBss(moreBss: List<Line>): CompilationResult<T> {
        return CompilationResult(
            value = value,
            text = text,
            bss = bss + moreBss
        )
    }

    companion object {
        val EMPTY = CompilationResult<List<Line>>(
            value = listOf(),
            text = listOf(),
            bss = listOf()
        )

        fun <T> of(value: T): CompilationResult<T> {
            return CompilationResult(
                value = value,
                text = listOf(),
                bss = listOf()
            )
        }

        fun flatten(results: List<CompilationResult<List<Line>>>): CompilationResult<List<Line>> {
            val value = results.flatMap { result -> result.value }
            val text = results.flatMap { result -> result.text }
            val bss = results.flatMap { result -> result.bss }
            return CompilationResult(
                value = value,
                text = text,
                bss = bss
            )
        }
    }
}

private fun CompilationResult<List<Line>>.instructionsToText(): CompilationResult<Unit> {
    return CompilationResult(
        value = Unit,
        text = value + text,
        bss = bss
    )
}

private class Compiler(private val moduleSet: ModuleSet) {
    fun compile(target: Path, mainModule: List<Identifier>) {
        val asm = mutableListOf(
            Directives.global("main"),
            Directives.defaultRel,
            Directives.textSection,
            Label("main"),
            Instructions.push(Registers.rbp),
            Instructions.mov(Registers.rbp, Registers.rsp)
        )

        asm.addAll(importModule(mainModule))
        asm.addAll(fieldAccess(Identifier("main"), moduleSet.module(mainModule)!!.type))
        asm.add(Instructions.pop(Registers.rdx))
        asm.add(Instructions.call(Registers.rdx))

        if (mainReturnsUnit(moduleSet, mainModule)) {
            asm.add(Instructions.mov(Registers.rax, 0))
        }

        asm.addAll(listOf(
            Instructions.mov(Registers.rsp, Registers.rbp),
            Instructions.pop(Registers.rbp),
            Instructions.ret
        ))

        val moduleAsm = generateAsmForModule(mainModule)
        asm.addAll(moduleAsm.text)
        asm.add(Directives.bssSection)
        asm.addAll(moduleAsm.bss)

        val source = serialise(asm)

        println(source.lines().mapIndexed { index, line ->
            (index + 1).toString().padStart(3) + " " + line
        }.joinToString("\n"))

        temporaryDirectory().use { directory ->
            val directoryPath = directory.file.toPath()
            val asmPath = directoryPath.resolve("program.asm")
            asmPath.toFile().writeText(source)
            org.shedlang.compiler.backends.tests.run(
                listOf("nasm", "-felf64", asmPath.toString()),
                directory.file
            ).throwOnError()
            org.shedlang.compiler.backends.tests.run(
                listOf("gcc", "program.o", "-o", target.toString()),
                directory.file
            ).throwOnError()
        }
    }

    private fun generateAsmForModule(moduleName: List<Identifier>): CompilationResult<Unit> {
        val image = loadModuleSet(moduleSet)
        val result = generateAsmForModuleInitialisation(image, moduleName)
        return result.addBss(listOf(
            Label(labelForModuleInitialised(moduleName)),
            Instructions.resq(1),
            Label(labelForModuleValue(moduleName)),
            Instructions.resq(fieldCount(moduleName))
        ))
    }

    private fun generateAsmForModuleInitialisation(
        image: Image,
        moduleName: List<Identifier>
    ): CompilationResult<Unit> {
        return generateAsmForInstructions(image.moduleInitialisation(moduleName))
            .mapInstructions { instructions ->
                listOf(
                    Label(labelForModuleInit(moduleName)),
                    Instructions.push(Registers.rbp),
                    Instructions.mov(Registers.rbp, Registers.rsp)
                    // TODO: space for locals
                ) + instructions + listOf(
                    Instructions.mov(Registers.rsp, Registers.rbp),
                    Instructions.pop(Registers.rbp),
                    Instructions.ret
                )
            }
            .instructionsToText()
    }

    private fun fieldCount(moduleName: List<Identifier>): Int {
        // TODO: handle modules with multiple fields
        return 1
    }

    private fun generateAsmForInstructions(instructions: List<Instruction>): CompilationResult<List<Line>> {
        return CompilationResult.flatten(
            instructions.map { instruction -> generateAsmForInstruction(instruction) }
        )
    }

    private fun generateAsmForInstruction(instruction: Instruction): CompilationResult<List<Line>> {
        return when (instruction) {
            is DeclareFunction -> {
                val label = generateLabel()
                // TODO: handle more locals
                val localCount = 1
                generateAsmForInstructions(instruction.bodyInstructions)
                    .mapInstructions { instructions ->
                        listOf(
                            Label(label),
                            Instructions.push(Registers.rbp),
                            Instructions.mov(Registers.rbp, Registers.rsp),
                            Instructions.sub(Registers.rsp, Immediates.int(VALUE_SIZE * localCount))
                        ) + instructions
                    }
                    .instructionsToText()
                    .mapInstructions {
                        listOf(
                            Instructions.lea(Registers.rdx, MemoryOperand(LabelOperand(label))),
                            Instructions.push(Registers.rdx)
                        )
                    }
            }
            is Exit -> {
                CompilationResult.EMPTY
            }
            is PushValue -> {
                CompilationResult.of(
                    listOf(Instructions.push(generateOperandForValue(instruction.value)))
                )
            }
            is Return -> {
                CompilationResult.of(
                    listOf(
                        Instructions.pop(Registers.rax),
                        Instructions.mov(Registers.rsp, Registers.rbp),
                        Instructions.pop(Registers.rbp),
                        Instructions.ret
                    )
                )
            }
            is LocalStore -> {
                // TODO: handle locals properly
                val localIndex = 0
                CompilationResult.of(
                    listOf(
                        Instructions.pop(Registers.rax),
                        Instructions.mov(
                            localOperand(localIndex),
                            Registers.rax
                        )
                    )
                )
            }
            is ModuleStore -> {
                CompilationResult.of(
                    listOf(
                        Instructions.lea(
                            Registers.rdx,
                            MemoryOperand(LabelOperand(labelForModuleInitialised(instruction.moduleName)))
                        ),
                        Instructions.mov(
                            MemoryOperand(Registers.rdx, operandSize = OperandSize.QWORD),
                            Immediates.qword(1)
                        ),
                        Instructions.mov(
                            Registers.rax,
                            localOperand(0)
                        ),
                        Instructions.lea(
                            Registers.rdx,
                            MemoryOperand(LabelOperand(labelForModuleValue(instruction.moduleName)))
                        ),
                        Instructions.mov(
                            MemoryOperand(Registers.rdx),
                            Registers.rax
                        )
                    )
                )
            }
            else -> throw UnsupportedOperationException(instruction.toString())
        }
    }

    private fun localOperand(localIndex: Int) =
        MemoryOperand(Registers.rbp, offset = (-1 - localIndex) * VALUE_SIZE)

    private fun generateOperandForValue(value: IrValue): Operand {
        if (value is IrInt) {
            return Immediates.qword(value.value.longValueExact())
        } else {
            throw UnsupportedOperationException(value.toString())
        }
    }

    private fun mainReturnsUnit(moduleSet: ModuleSet, mainModuleName: List<Identifier>): Boolean {
        val mainModule = moduleSet.module(mainModuleName)!!
        val mainType = mainModule.type.fieldType(Identifier("main")) as FunctionType
        return mainType.returns == UnitType
    }

    private fun importModule(moduleName: List<Identifier>): List<Line> {
        val alreadyInitialisedLabel = generateLabel()
        return listOf(
            Instructions.lea(
                Registers.rdx,
                MemoryOperand(LabelOperand(labelForModuleInitialised(moduleName)))
            ),
            Instructions.cmp(
                MemoryOperand(Registers.rdx, operandSize = OperandSize.QWORD),
                Immediates.qword(0)
            ),
            Instructions.jne(LabelOperand(alreadyInitialisedLabel)),
            Instructions.call(labelForModuleInit(moduleName)),
            Label(alreadyInitialisedLabel),

            Instructions.lea(
                Registers.rdx,
                MemoryOperand(LabelOperand(labelForModuleValue(moduleName)))
            ),
            Instructions.push(Registers.rdx)
        )
    }

    private fun fieldAccess(fieldName: Identifier, type: ModuleType): List<Line> {
        return listOf(
            Instructions.pop(Registers.rax),
            // TODO: field index
            Instructions.push(MemoryOperand(Registers.rax, operandSize = OperandSize.QWORD))
        )
    }

    private fun labelForModuleInitialised(moduleName: List<Identifier>): String {
        return "shed__module_initialised__" + moduleNameToLabel(moduleName)
    }

    private fun labelForModuleValue(moduleName: List<Identifier>): String {
        return "shed__module_value__" + moduleNameToLabel(moduleName)
    }

    private fun labelForModuleInit(moduleName: List<Identifier>): String {
        return "shed__module_init__" + moduleNameToLabel(moduleName)
    }

    private fun moduleNameToLabel(moduleName: List<Identifier>) =
        moduleName.joinToString("_") { part -> part.value }

    private var nextLabelIndex = 0

    private fun generateLabel(): String {
        return "shed_label_" + nextLabelIndex++
    }
}
