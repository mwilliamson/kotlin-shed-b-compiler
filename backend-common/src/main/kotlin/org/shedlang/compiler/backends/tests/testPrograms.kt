package org.shedlang.compiler.backends.tests

import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Path

data class TestProgram(
    val name: String,
    val source: String,
    val expectedResult: ExecutionResult
)

val testPrograms = listOf(
    TestProgram(
        name = "recursive factorial",
        source = """
            module example;

            fun fact(n: Int) : Int {
                if (n == 0) {
                    return 1;
                } else {
                    return n * fact(n - 1);
                }
            }

            fun main() : Unit {
                print(intToString(fact(5)));
            }
        """.trimIndent(),
        expectedResult = ExecutionResult(stdout = "120"))
)



fun run(arguments: List<String>): ExecutionResult {
    return run(arguments, null)
}

private fun run(arguments: List<String>, directoryPath: Path?): ExecutionResult {
    val process = ProcessBuilder(*arguments.toTypedArray())
        .directory(directoryPath?.toFile())
        .start()

    val exitCode = process.waitFor()
    val output = readString(process.inputStream).trim()
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
