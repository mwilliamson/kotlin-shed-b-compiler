package org.shedlang.compiler.cli

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.InvalidArgumentException
import com.xenomachina.argparser.default
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.ast.parseModuleName
import org.shedlang.compiler.backends.Backend
import org.shedlang.compiler.backends.llvm.LlvmBackend
import org.shedlang.compiler.backends.wasm.WasmBackend


private val backends = mapOf(
    "llvm" to LlvmBackend,
    "wasm" to WasmBackend,
)

internal fun ArgParser.shedSource(): ArgParser.Delegate<String> {
    return this.positional("SOURCE", help = "path to source root")
}

internal fun ArgParser.shedBackends(): ArgParser.Delegate<Backend> {
    return this.choices("--backend", argName = "BACKEND", help = "backend to generate code with", choices = backends)
}

internal fun ArgParser.shedMainModule(): ArgParser.Delegate<ModuleName?> {
    return this.storing("-m", argName = "MAIN", help = "main module to run", transform = ::parseModuleName).default(null)
}


internal fun <T> ArgParser.choices(
    vararg names: String,
    argName: String,
    help: String,
    choices: Map<String, T>
): ArgParser.Delegate<T> {
    val keys = choices.keys.sorted()
    return option<T>(
        *names,
        help = keys.joinToString("|") + "\n" + help,
        argNames = listOf(argName),
        handler = {
            val name = arguments.first()
            val choice = choices[name]
            if (choice == null) {
                val choicesString = keys.joinToString(", ") { key -> "'$key'" }
                throw InvalidArgumentException("argument $argName: invalid choice '$name' (choose from $choicesString)")
            } else {
                choice
            }
        }
    )
}
