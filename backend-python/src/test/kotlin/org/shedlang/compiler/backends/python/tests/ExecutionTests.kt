package org.shedlang.compiler.backends.python.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.backends.python.generateCode
import org.shedlang.compiler.backends.python.serialise
import org.shedlang.compiler.read
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Path

class ExecutionTests {
    @Test
    fun factorial() {
        val source = """
            module example;

            fun fact(n: Int) : Int {
                if (n == 0) {
                    return 1;
                } else {
                    return n * fact(n - 1);
                }
            }
        """.trimIndent()
        val module = read(filename = "<string>", input = source)
        val generateCode = generateCode(module)
        val contents = serialise(generateCode) + "\nprint(fact(5))\n"
        val result = run(listOf("python", "-c", contents))
        result.assertSuccess()
        assertThat(result.stdout, equalTo("120"))
    }


    fun run(arguments: List<String>): ExecutionResult {
        return run(arguments, null)
    }

    fun run(arguments: List<String>, directoryPath: Path?): ExecutionResult {
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

    class ExecutionResult(val exitCode: Int, val stdout: String, val stderr: String) {
        fun assertSuccess() {
            if (exitCode != 0) {
                throw RuntimeException("stderr was: " + stderr)
            }
        }
    }
}
