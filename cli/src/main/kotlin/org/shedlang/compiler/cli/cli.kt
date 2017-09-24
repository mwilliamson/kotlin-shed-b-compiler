package org.shedlang.compiler.cli

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.mainBody
import org.shedlang.compiler.readDirectory
import java.nio.file.Paths

object ShedCli {
    @JvmStatic
    fun main(rawArguments: Array<String>) {
        val exitCode = mainBody {
            run(rawArguments)
        }
        System.exit(exitCode)
    }

    fun run(rawArguments: Array<String>): Int {
        val arguments = Arguments(ArgParser(rawArguments))
        val result = readDirectory(Paths.get(arguments.source))

        val tempDir = createTempDir()
        try {
            arguments.backend.compile(result, target = tempDir.toPath())
            return arguments.backend.run(tempDir.toPath(), arguments.mainModule.split("."))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private class Arguments(parser: ArgParser) {
        val source by parser.shedSource()
        val mainModule by parser.positional("MAIN", help = "main module to run")
        val backend by parser.shedBackends()

        init {
            parser.force()
        }
    }
}

object ShedcCli {
    @JvmStatic
    fun main(rawArguments: Array<String>) {
        return mainBody {
            run(rawArguments)
        }
    }

    private fun run(rawArguments: Array<String>) {
        val arguments = Arguments(ArgParser(rawArguments))
        val result = readDirectory(Paths.get(arguments.source))
        arguments.backend.compile(result, target = Paths.get(arguments.outputPath))
    }

    private class Arguments(parser: ArgParser) {
        val source by parser.shedSource()
        val outputPath by parser.storing("--output-path",   "-o", help = "path to output directory")
        val backend by parser.shedBackends()

        init {
            parser.force()
        }
    }
}
