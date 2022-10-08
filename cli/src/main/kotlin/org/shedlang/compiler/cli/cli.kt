package org.shedlang.compiler.cli

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.InvalidArgumentException
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.backends.Backend
import org.shedlang.compiler.frontend.readPackageModule
import org.shedlang.compiler.frontend.readStandaloneModule
import org.shedlang.compiler.stackinterpreter.RealWorld
import org.shedlang.compiler.stackinterpreter.executeMain
import org.shedlang.compiler.stackir.loadModuleSet
import org.shedlang.compiler.frontend.standaloneModulePathToName
import org.shedlang.compiler.SourceError
import org.shedlang.compiler.backends.createTempDirectory
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
        val separatorIndex = rawArguments.indexOf("--")
        val (shedRawArguments, programArguments) = if (separatorIndex == -1) {
            Pair(rawArguments, listOf())
        } else {
            Pair(rawArguments.copyOfRange(0, separatorIndex), rawArguments.copyOfRange(separatorIndex + 1, rawArguments.size).toList())
        }

        val shedArguments = Arguments(ArgParser(shedRawArguments))

        val sourcePath = Paths.get(shedArguments.source)
        val backend = shedArguments.backend
        val mainModuleNameArgument = shedArguments.mainModule

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
                    world = RealWorld(args = programArguments)
                )
            }
        } else {
            val tempDir = createTempDirectory()
            val target = tempDir.resolve("target")
            try {
                val mainModuleName = compile(
                    sourcePath = sourcePath,
                    mainModuleNameArgument = mainModuleNameArgument,
                    backend = backend,
                    target = target
                )
                return backend.run(target, mainModuleName, args = programArguments)
            } finally {
                tempDir.toFile().deleteRecursively()
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

object ShedBindingsCli {
    @JvmStatic
    fun main(rawArguments: Array<String>) {
        return mainBody {
            run(rawArguments)
        }
    }

    private fun run(rawArguments: Array<String>) {
        val arguments = Arguments(ArgParser(rawArguments))
        arguments.backend.generateBindings(Paths.get(arguments.outputPath))
    }

    private class Arguments(parser: ArgParser) {
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
