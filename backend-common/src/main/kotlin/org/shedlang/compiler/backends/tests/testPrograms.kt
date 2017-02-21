package org.shedlang.compiler.backends.tests

import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Path
import java.nio.file.Paths

data class TestProgram(
    val name: String,
    val source: String,
    val expectedResult: ExecutionResult
)

fun testPrograms(): List<TestProgram> {
    val root = findRoot()
    val exampleDirectory = root.resolve("examples").toFile()
    val exampleFilenames = exampleDirectory.list()

    return exampleFilenames.toList().map(fun(filename): TestProgram {
        val text = exampleDirectory.resolve(filename).readText()
        val name = Regex("^// name:\\s*(.*)\\s*$", setOf(RegexOption.MULTILINE)).find(text)!!.groupValues[1]
        val stdout = Regex("^// stdout:((?:\n//   .*)*)", setOf(RegexOption.MULTILINE))
            .find(text)!!
            .groupValues[1]
            .trimMargin("//   ") + "\n"
        return TestProgram(
            name = name,
            source = text,
            expectedResult = ExecutionResult(stdout = stdout)
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

private fun run(arguments: List<String>, directoryPath: Path?): ExecutionResult {
    val process = ProcessBuilder(*arguments.toTypedArray())
        .directory(directoryPath?.toFile())
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
