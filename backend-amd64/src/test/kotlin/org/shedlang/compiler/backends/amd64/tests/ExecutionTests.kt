package org.shedlang.compiler.backends.amd64.tests

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.Module
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.amd64.*
import org.shedlang.compiler.backends.tests.temporaryDirectory
import org.shedlang.compiler.backends.tests.testPrograms
import org.shedlang.compiler.typechecker.CompilerError
import org.shedlang.compiler.typechecker.SourceError
import org.shedlang.compiler.types.FunctionType
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
                    compile(
                        testProgram.load(),
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

    private fun compile(moduleSet: ModuleSet, target: Path, mainModule: List<Identifier>) {
        val asm = mutableListOf(
            Directives.global("main"),
            Directives.defaultRel,
            Directives.section(".text"),
            Label("main"),
            Instructions.call(labelForFunction(mainModule, Identifier("main")))
        )

        if (mainReturnsUnit(moduleSet, mainModule)) {
            asm.add(Instructions.mov(Registers.rax, 0))
        }

        asm.add(Instructions.ret)

        asm.addAll(generateAsmForModuleSet(moduleSet))

        val source = serialise(asm)

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

    private fun generateAsmForModuleSet(moduleSet: ModuleSet): Collection<Line> {
        return moduleSet.modules.flatMap { module -> generateAsmForModule(module) }
    }

    private fun generateAsmForModule(module: Module): List<Line> {
        return when (module) {
            is Module.Shed -> module.node.body.flatMap { statement -> generateAsmForStatement(module, statement) }
            is Module.Native -> listOf()
        }
    }

    private fun generateAsmForStatement(module: Module, statement: ModuleStatementNode): List<Line> {
        return statement.accept(object : ModuleStatementNode.Visitor<List<Line>> {
            override fun visit(node: TypeAliasNode): List<Line> {
                return listOf()
            }

            override fun visit(node: ShapeNode): List<Line> {
                return listOf()
            }

            override fun visit(node: UnionNode): List<Line> {
                return listOf()
            }

            override fun visit(node: FunctionDeclarationNode): List<Line> {
                return listOf(
                    Label(labelForFunction(module.name, node.name)),
                    Instructions.mov(Registers.rax, 42),
                    Instructions.ret
                )
            }

            override fun visit(node: ValNode): List<Line> {
                return listOf()
            }

            override fun visit(node: VarargsDeclarationNode): List<Line> {
                return listOf()
            }

        })
    }

    private fun mainReturnsUnit(moduleSet: ModuleSet, mainModuleName: List<Identifier>): Boolean {
        val mainModule = moduleSet.module(mainModuleName)!!
        val mainType = mainModule.type.fieldType(Identifier("main")) as FunctionType
        return mainType.returns == UnitType
    }

    private fun labelForFunction(moduleName: List<Identifier>, functionName: Identifier): String {
        return moduleName.joinToString("_") { part -> part.value } + "__" + functionName.value
    }
}
