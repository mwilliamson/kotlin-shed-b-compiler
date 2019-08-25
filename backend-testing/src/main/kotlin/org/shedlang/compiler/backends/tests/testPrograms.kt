package org.shedlang.compiler.backends.tests

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.installDependencies
import org.shedlang.compiler.readPackage
import org.shedlang.compiler.readStandalone
import org.shedlang.compiler.tests.findRoot
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Path

data class TestProgram(
    val name: String,
    private val source: TestProgramSource,
    val expectedResult: Matcher<ExecutionResult>
) {
    fun load(): ModuleSet {
        return when (source) {
            is TestProgramSource.File -> {
                readStandalone(source.path)
            }
            is TestProgramSource.Directory -> {
                installDependencies(source.path)
                readPackage(source.path, source.mainModule)
            }
        }
    }

    val mainModule: List<Identifier>
        get() = source.mainModule
}

sealed class TestProgramSource {
    abstract val path: Path
    abstract val mainModule: List<Identifier>
    abstract val expectedResult: Matcher<ExecutionResult>?

    class File(
        override val path: Path,
        override val expectedResult: Matcher<ExecutionResult>?
    ): TestProgramSource() {
        override val mainModule: List<Identifier>
            get() = listOf(Identifier("Main"))
    }

    class Directory(
        override val path: Path,
        override val mainModule: List<Identifier>,
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
}

fun temporaryDirectory(): TemporaryDirectory {
    val file = createTempDir()
    return TemporaryDirectory(file)
}

class TemporaryDirectory(val file: File) : Closeable {
    override fun close() {
        file.deleteRecursively()
    }
}
