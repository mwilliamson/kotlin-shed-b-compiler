@file:JvmName("Cli")

package org.shedlang.compiler.cli

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.mainBody
import org.shedlang.compiler.backends.python.compile
import org.shedlang.compiler.readDirectory
import java.nio.file.Paths

fun main(rawArguments: Array<String>) {
    mainBody {
        val arguments = Arguments(ArgParser(rawArguments))
        val result = readDirectory(Paths.get(arguments.source))
        compile(result, target = Paths.get(arguments.outputPath))
    }
}

private class Arguments(parser: ArgParser) {
    val source by parser.positional("SOURCE", help = "path to source root")
    val outputPath by parser.storing("--output-path",   "-o", help = "path to output directory")

    init {
        parser.force()
    }
}
