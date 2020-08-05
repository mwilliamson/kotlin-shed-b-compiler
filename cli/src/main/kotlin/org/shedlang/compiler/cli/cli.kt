package org.shedlang.compiler.cli

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.InvalidArgumentException
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.backends.Backend
import org.shedlang.compiler.readPackageModule
import org.shedlang.compiler.readStandaloneModule
import org.shedlang.compiler.stackinterpreter.RealWorld
import org.shedlang.compiler.stackinterpreter.executeMain
import org.shedlang.compiler.stackir.loadModuleSet
import org.shedlang.compiler.standaloneModulePathToName
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

            val sourcePath = Paths.get(arguments.source)
            val backend = arguments.backend
            val mainModuleNameArgument = arguments.mainModule

            if (backend == null) {
                return onErrorPrintAndExit {
                    val (mainModuleName, moduleSet) = read(
                        sourcePath = sourcePath,
                        mainModuleNameArgument = mainModuleNameArgument
                    )

                    val image = loadModuleSet(moduleSet)

                    executeMain(
                        mainModule = mainModuleName,
                    image = image,
                    world = RealWorld
                )
            }
        } else {
            val tempDir = createTempDir()
            val target = tempDir.resolve("target").toPath()
            try {
                val mainModuleName = compile(
                    sourcePath = sourcePath,
                    mainModuleNameArgument = mainModuleNameArgument,
                    backend = backend,
                    target = target
                )
                return backend.run(target, mainModuleName)
            } finally {
                tempDir.deleteRecursively()
            }
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
            sourcePath = Paths.get(arguments.source),
            mainModuleNameArgument = arguments.mainModule,
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

private fun read(sourcePath: Path, mainModuleNameArgument: ModuleName?): Pair<ModuleName, ModuleSet> {
    return if (sourcePath.toFile().isDirectory) {
        if (mainModuleNameArgument == null) {
            throw InvalidArgumentException("module name is required when using source directory")
        } else {
            Pair(
                mainModuleNameArgument,
                readPackageModule(sourcePath, mainModuleNameArgument)
            )
        }
    } else {
        Pair(
            standaloneModulePathToName(sourcePath),
            readStandaloneModule(sourcePath)
        )
    }
}

private fun compile(sourcePath: Path, mainModuleNameArgument: ModuleName?, backend: Backend, target: Path): ModuleName {
    return onErrorPrintAndExit {
        val (mainModuleName, moduleSet) = read(sourcePath = sourcePath, mainModuleNameArgument = mainModuleNameArgument)
        backend.compile(moduleSet, mainModule = mainModuleName, target = target)
        mainModuleName
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
