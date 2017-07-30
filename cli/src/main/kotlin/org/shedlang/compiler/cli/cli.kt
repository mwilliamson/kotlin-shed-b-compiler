@file:JvmName("Cli")

package org.shedlang.compiler.cli

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.shedlang.compiler.backends.python.compile
import org.shedlang.compiler.read
import java.nio.file.Paths

fun main(rawArguments: Array<String>) {
    val arguments = parseArguments(rawArguments)
    for (arg in arguments.args) {
        val result = read(
            base = Paths.get("."),
            path = Paths.get(arg)
        )
        compile(result, target = Paths.get(arguments.getOptionValue("o")))
    }
}

private fun parseArguments(rawArguments: Array<String>): CommandLine {
    val options = Options()

    options.addOption(Option.builder("o").hasArg().required().build())

    return DefaultParser().parse(options, rawArguments)
}
