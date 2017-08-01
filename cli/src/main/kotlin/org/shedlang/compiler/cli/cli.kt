@file:JvmName("Cli")

package org.shedlang.compiler.cli

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.mainBody
import org.shedlang.compiler.backends.Backend
import org.shedlang.compiler.readDirectory
import java.nio.file.Paths

fun main(rawArguments: Array<String>) {
    mainBody {
        val backends = mapOf(
            "python" to org.shedlang.compiler.backends.python.backend,
            "javascript" to org.shedlang.compiler.backends.javascript.backend
        )

        val arguments = Arguments(ArgParser(rawArguments), backends)
        val result = readDirectory(Paths.get(arguments.source))
        arguments.backend.compile(result, target = Paths.get(arguments.outputPath))
    }
}

private class Arguments(parser: ArgParser, backends: Map<String, Backend>) {
    val source by parser.positional("SOURCE", help = "path to source root")
    val outputPath by parser.storing("--output-path",   "-o", help = "path to output directory")
    val backend by parser.choices("--backend", argName = "BACKEND", help = "backend to generate code with", choices = backends)

    init {
        parser.force()
    }
}

private fun <T> ArgParser.choices(vararg names: String, argName: String, help: String, choices: Map<String, T>): ArgParser.Delegate<T> {
    return option<T>(
        *names,
        help = choices.keys.joinToString("|") + "\n" + help,
        argNames = listOf(argName),
        handler = {
            choices[arguments.first()]!!
        }
    )
}
