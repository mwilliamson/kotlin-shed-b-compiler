package org.shedlang.compiler.cli

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.backends.Backend
import org.shedlang.compiler.readPackageModule
import org.shedlang.compiler.stackinterpreter.RealWorld
import org.shedlang.compiler.stackinterpreter.executeMain
import org.shedlang.compiler.stackir.loadModuleSet
import org.shedlang.compiler.typechecker.SourceError
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

        val tempDir = createTempDir()
        try {
            val base = Paths.get(arguments.source)
            val backend = arguments.backend
            if (backend == null) {
                return onErrorPrintAndExit {
                    val source = readPackageModule(base, arguments.mainModule)
                    val image = loadModuleSet(source)
                    executeMain(
                        mainModule = arguments.mainModule,
                        image = image,
                        world = RealWorld
                    )
                }
            } else {
                compile(
                    base = base,
                    mainName = arguments.mainModule,
                    backend = backend,
                    target = tempDir.toPath()
                )
                return backend.run(tempDir.toPath(), arguments.mainModule)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private class Arguments(parser: ArgParser) {
        val source by parser.shedSource()
        val mainModule by parser.shedMainModule()
        val backend by parser.shedBackends().default(null)

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
        val mainModule by parser.shedMainModule()
        val outputPath by parser.storing("--output-path",   "-o", help = "path to output directory")
        val backend by parser.shedBackends()

        init {
            parser.force()
        }
    }
}

private fun compile(base: Path, mainName: ModuleName, backend: Backend, target: Path) {
    onErrorPrintAndExit {
        val result = readPackageModule(base, mainName)
        backend.compile(result, mainModule = mainName, target = target)
    }
}

private fun <T> onErrorPrintAndExit(func: () -> T): T {
    try {
        return func()
    } catch (error: SourceError) {
        System.err.println("Error: " + error.message)
        System.err.println(error.source.describe())
        exitProcess(2)
    }
}
