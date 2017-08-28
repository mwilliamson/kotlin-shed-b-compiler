package org.shedlang.compiler.backends.tests

import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Path
import java.nio.file.Paths

data class TestProgram(
    val name: String,
    val base: Path,
    val main: Path,
    val expectedResult: ExecutionResult
)

private data class TestProgramFiles(val base: Path, val main: Path)

fun testPrograms(): List<TestProgram> {
    return findTestFiles().map(fun(file): TestProgram {
        val text = file.base.resolve(file.main).toFile().readText()

        val name = Regex("^// name:\\s*(.*)\\s*$", setOf(RegexOption.MULTILINE)).find(text)!!.groupValues[1]

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
            name = name,
            base = file.base,
            main = file.main,
            expectedResult = ExecutionResult(stdout = stdout, exitCode = exitCode)
        )
    })
}

private fun findTestFiles(): List<TestProgramFiles> {
    val exampleDirectory = findRoot().resolve("examples")
    return exampleDirectory.toFile().list().map(fun(name): TestProgramFiles {
        val file = exampleDirectory.resolve(name)
        val main = if (file.toFile().isDirectory) {
            file.resolve("main.shed")
        } else {
            file
        }
        return TestProgramFiles(
            base = exampleDirectory,
            main = exampleDirectory.relativize(main)
        )
    })
}

private fun findRoot(): Path {
    val rootFilenames = listOf("examples", "frontend")
    var directory = Paths.get(System.getProperty("user.dir"))
    while (!directory.toFile().list().toList().containsAll(rootFilenames)) {
        directory = directory.parent
    }
    return directory
}

fun run(arguments: List<String>): ExecutionResult {
    return run(arguments, null)
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
    fun assertSuccess() {
        if (exitCode != 0) {
            throw RuntimeException("stderr was: " + stderr)
        }
    }
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
