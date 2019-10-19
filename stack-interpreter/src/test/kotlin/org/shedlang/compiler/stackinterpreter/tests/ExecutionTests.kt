package org.shedlang.compiler.stackinterpreter.tests

import com.natpryce.hamkrest.assertion.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.shedlang.compiler.Module
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.FunctionDeclarationNode
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleStatementNode
import org.shedlang.compiler.backends.tests.ExecutionResult
import org.shedlang.compiler.backends.tests.TestProgram
import org.shedlang.compiler.backends.tests.testPrograms
import org.shedlang.compiler.stackinterpreter.*
import org.shedlang.compiler.typechecker.CompilerError
import org.shedlang.compiler.typechecker.SourceError

class ExecutionTests {
    private val disabledTests = setOf<String>(
        "cast",
        "ConstantField.shed",
        "dependencies",
        "localImports",
        "moduleName",
        "ShapeTypeInfo.shed",
        "stdlib",
        "symbols",
        "TailRec.shed",
        "usingStdlib",
        "When.shed",
        "WhenElse.shed"
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
                    val finalState = executeInstructions(
                        persistentListOf(
                            InitModule(testProgram.mainModule),
                            LoadModule(testProgram.mainModule),
                            FieldAccess(Identifier("main")),
                            Call(positionalArgumentCount = 0, namedArgumentNames = listOf())
                        ),
                        image = image,
                        defaultVariables = builtinVariables,
                        world = world
                    )

                    val finalValue = finalState.popTemporary().second
                    val exitCode = when (finalValue) {
                        is InterpreterInt -> finalValue.value.toInt()
                        is InterpreterUnit -> 0
                        else -> throw Exception("final value was: $finalValue")
                    }

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
