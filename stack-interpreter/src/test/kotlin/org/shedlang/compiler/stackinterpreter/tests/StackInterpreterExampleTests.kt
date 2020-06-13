package org.shedlang.compiler.stackinterpreter.tests

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.CompilerError
import org.shedlang.compiler.Module
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.FunctionDeclarationNode
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleStatementNode
import org.shedlang.compiler.backends.tests.ExecutionResult
import org.shedlang.compiler.backends.tests.TestProgram
import org.shedlang.compiler.backends.tests.testPrograms
import org.shedlang.compiler.stackinterpreter.World
import org.shedlang.compiler.stackinterpreter.executeMain
import org.shedlang.compiler.stackir.loadModuleSet
import org.shedlang.compiler.typechecker.SourceError

class StackInterpreterExampleTests {
    private val disabledTests = setOf<String>(
        "ConstantField.shed",
        "Resume.shed",
        "TailRec.shed"
    )

    @TestFactory
    fun testProgram(): List<DynamicTest> {
        return testPrograms().filter { testProgram ->
            !disabledTests.contains(testProgram.name)
        }.mapNotNull { testProgram ->
            DynamicTest.dynamicTest(testProgram.name) {
                try {
                    val modules = testProgram.load()
                    val image = loadModuleSet(modules)
                    val mainFunction = findMainFunction(modules, testProgram)
                    val world = InMemoryWorld()
                    val exitCode = executeMain(
                        mainModule = testProgram.mainModule,
                        image = image,
                        world = world
                    )

                    val executionResult = ExecutionResult(
                        exitCode = exitCode,
                        stderr = "",
                        stdout = world.stdout
                    )
                    assertThat(executionResult, testProgram.expectedResult)
                } catch (error: SourceError) {
                    print(error.source.describe())
                    throw error
                } catch (error: CompilerError) {
                    print(error.source.describe())
                    throw error
                }
            }
        }
    }

    private fun findMainFunction(modules: ModuleSet, testProgram: TestProgram): ModuleStatementNode {
        val mainModule = modules.modules.find { module ->
            module.name == testProgram.mainModule
        }!! as Module.Shed
        return mainModule.node.body.find { statement ->
            statement is FunctionDeclarationNode && statement.name == Identifier("main")
        }!!
    }
}

class InMemoryWorld : World {
    private val stdoutBuilder: StringBuilder = StringBuilder()

    override fun writeToStdout(value: String) {
        stdoutBuilder.append(value)
    }

    val stdout: String
        get() = stdoutBuilder.toString()

}
