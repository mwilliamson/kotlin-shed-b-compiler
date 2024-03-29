package org.shedlang.compiler.backends.tests

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.shedlang.compiler.*
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.backends.createTempDirectory
import org.shedlang.compiler.frontend.installDependencies
import org.shedlang.compiler.frontend.readPackageModule
import org.shedlang.compiler.frontend.readStandaloneModule
import org.shedlang.compiler.frontend.standaloneModulePathToName
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Path

data class TestProgram(
    val name: String,
    private val source: TestProgramSource,
    val args: List<String>,
    val expectedResult: Matcher<ExecutionResult>
) {
    fun load(): ModuleSet {
        return when (source) {
            is TestProgramSource.File -> {
                readStandaloneModule(source.path)
            }
            is TestProgramSource.Directory -> {
                installDependencies(source.path)
                readPackageModule(source.path, source.mainModule)
            }
        }
    }

    val mainModule: ModuleName
        get() = source.mainModule
}

sealed class TestProgramSource {
    abstract val path: Path
    abstract val mainModule: ModuleName
    abstract val expectedResult: Matcher<ExecutionResult>?

    class File(
        override val path: Path,
        override val expectedResult: Matcher<ExecutionResult>?
    ): TestProgramSource() {
        override val mainModule: ModuleName
            get() = standaloneModulePathToName(path)
    }

    class Directory(
        override val path: Path,
        override val mainModule: ModuleName,
        override val expectedResult: Matcher<ExecutionResult>?
    ): TestProgramSource()
}

fun testPrograms(): List<TestProgram> {
    return findTestFiles().map(fun(source): TestProgram {
        val mainPath = when (source) {
            is TestProgramSource.File -> source.path
            is TestProgramSource.Directory -> source.path.resolve("src").resolve(source.mainModule.map(Identifier::value).joinToString("/") + ".shed")
        }
        val text = mainPath.toFile().readText()


        val argsMatch = Regex("^// args:\\s*(.*)\\s*$", setOf(RegexOption.MULTILINE)).find(text)
        val args = if (argsMatch == null) {
            listOf()
        } else {
            argsMatch.groupValues[1].split(Regex("\\s+"))
        }

        val exitCodeMatch = Regex("^// exitCode:\\s*(.*)\\s*$", setOf(RegexOption.MULTILINE)).find(text)
        val exitCode = if (exitCodeMatch == null) {
            0
        } else {
            exitCodeMatch.groupValues[1].toInt()
        }

        val stdoutMatch = Regex("^// stdout:((?:\n//   .*)*)", setOf(RegexOption.MULTILINE))
            .find(text)
        val stdout = if (stdoutMatch == null) {
            ""
        } else {
            stdoutMatch
                .groupValues[1]
                .trimMargin("//   ") + "\n"
        }

        return TestProgram(
            name = source.path.fileName.toString(),
            source = source,
            args = args,
            expectedResult = source.expectedResult ?: equalTo(ExecutionResult(stdout = stdout, exitCode = exitCode))
        )
    })
}

private fun findTestFiles(): List<TestProgramSource> {
    val root = findRoot()
    val exampleDirectory = root.resolve("examples")
    val stdlibTestsSource = TestProgramSource.Directory(
        root.resolve("stdlib"),
        listOf(Identifier("StdlibTests"), Identifier("Main")),
        has(ExecutionResult::exitCode, equalTo(0))
    )
    return exampleDirectory.toFile().list().mapNotNull(fun(name): TestProgramSource? {
        val file = exampleDirectory.resolve(name)
        if (file.toFile().isDirectory) {
            return TestProgramSource.Directory(file, listOf(Identifier("Main")), null)
        } else {
            return TestProgramSource.File(file, null)
        }
    }) + listOf(stdlibTestsSource)
}

fun run(arguments: List<String>): ExecutionResult {
    return run(arguments, workingDirectory = null as Path?)
}

fun run(arguments: List<String>, workingDirectory: Path?): ExecutionResult {
    return run(arguments, workingDirectory = workingDirectory?.toFile())
}

fun run(arguments: List<String>, workingDirectory: File?): ExecutionResult {
    val process = ProcessBuilder(*arguments.toTypedArray())
        .directory(workingDirectory)
        .start()

    val exitCode = process.waitFor()
    val output = readString(process.inputStream)
    val stderrOutput = readString(process.errorStream)
    return ExecutionResult(exitCode, output, stderrOutput)
}

private fun readString(stream: InputStream): String {
    return InputStreamReader(stream, Charsets.UTF_8).use(InputStreamReader::readText)
}

data class ExecutionResult(val exitCode: Int = 0, val stdout: String = "", val stderr: String = "") {
    fun throwOnError(): ExecutionResult {
        if (exitCode == 0) {
            return this
        } else {
            throw Exception("exit code was $exitCode\nstdout:\n$stdout\nstderr:\n$stderr")
        }
    }
}

fun temporaryDirectory(): TemporaryDirectory {
    val file = createTempDirectory()
    return TemporaryDirectory(file.toFile())
}

class TemporaryDirectory(val file: File) : Closeable {
    val path: Path
        get() = file.toPath()

    override fun close() {
        file.deleteRecursively()
    }
}
