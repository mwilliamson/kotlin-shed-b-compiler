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
        "Cons.shed",
        "ConstantField.shed",
        "dependencies",
        "FieldDestructuring.shed",
        "localImports",
        "Matchers.shed",
        "moduleName",
        "PolymorphicCons.shed",
        "PolymorphicForEach.shed",
        "PolymorphicIdentity.shed",
        "PolymorphicMap.shed",
        "RecursiveFibonacci.shed",
        "ShapeTypeInfo.shed",
        "stdlib",
        "symbols",
        "TailRec.shed",
        "Tuples.shed",
        "TypeAlias.shed",
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
                    val finalState = executeInstructions(
                        persistentListOf(
                            InitModule(testProgram.mainModule),
                            LoadModule(testProgram.mainModule),
                            FieldAccess(Identifier("main")),
                            Call(argumentCount = 0)
                        ),
                        image = image,
                        defaultVariables = builtinVariables
                    )

                    val finalValue = finalState.popTemporary().second
                    val exitCode = when (finalValue) {
                        is InterpreterInt -> finalValue.value.toInt()
                        is InterpreterUnit -> 0
                        else -> throw Exception("final value was: $finalValue")
                    }

                    val executionResult = ExecutionResult(
                        exitCode = exitCode.toInt(),
                        stderr = "",
                        stdout = finalState.stdout
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
