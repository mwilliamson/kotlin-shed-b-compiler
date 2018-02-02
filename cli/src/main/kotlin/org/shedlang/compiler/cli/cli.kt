package org.shedlang.compiler.cli

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.mainBody
import org.shedlang.compiler.backends.Backend
import org.shedlang.compiler.readPackage
import org.shedlang.compiler.typechecker.TypeCheckError
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

object ShedCli {
    @JvmStatic
    fun main(rawArguments: Array<String>) {
        val exitCode = mainBody {
            run(rawArguments)
        }
        System.exit(exitCode)
    }

    private fun run(rawArguments: Array<String>): Int {
        val arguments = Arguments(ArgParser(rawArguments))
        val mainName = arguments.mainModule.split(".")

        val tempDir = createTempDir()
        try {
            compile(
                base = Paths.get(arguments.source),
                mainName = arguments.mainModule,
                backend = arguments.backend,
                target = tempDir.toPath()
            )
            return arguments.backend.run(tempDir.toPath(), mainName)
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
        compile(
            base = Paths.get(arguments.source),
            mainName = arguments.mainModule,
            backend = arguments.backend,
            target = Paths.get(arguments.outputPath)
        )
    }

    private class Arguments(parser: ArgParser) {
        val source by parser.shedSource()
        val mainModule by parser.positional("MAIN", help = "main module to run")
        val outputPath by parser.storing("--output-path",   "-o", help = "path to output directory")
        val backend by parser.shedBackends()

        init {
            parser.force()
        }
    }
}

private fun compile(base: Path, mainName: String, backend: Backend, target: Path) {
    try {
        val result = readPackage(base, mainName.split("."))
        backend.compile(result, target = target)
    } catch (error: TypeCheckError) {
        System.err.println("Error: " + error.message)
        System.err.println(error.source.describe())
        exitProcess(2)
    }
}
