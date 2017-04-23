package org.shedlang.compiler.backends.tests

import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Path
import java.nio.file.Paths

data class TestProgram(
    val name: String,
    val path: Path,
    val expectedResult: ExecutionResult
)

fun testPrograms(): List<TestProgram> {
    return findTestFiles().map(fun(file): TestProgram {
        val text = file.readText()
        val name = Regex("^// name:\\s*(.*)\\s*$", setOf(RegexOption.MULTILINE)).find(text)!!.groupValues[1]
        val stdout = Regex("^// stdout:((?:\n//   .*)*)", setOf(RegexOption.MULTILINE))
            .find(text)!!
            .groupValues[1]
            .trimMargin("//   ") + "\n"
        return TestProgram(
            name = name,
            path = file.toPath(),
            expectedResult = ExecutionResult(stdout = stdout)
        )
    })
}

private fun findTestFiles(): List<File> {
    val root = findRoot()
    val exampleDirectory = root.resolve("examples").toFile()
    return exampleDirectory.list().map(fun(name): File {
        val file = exampleDirectory.resolve(name)
        if (file.isDirectory) {
            return file.resolve("main.shed")
        } else {
            return file
        }
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
