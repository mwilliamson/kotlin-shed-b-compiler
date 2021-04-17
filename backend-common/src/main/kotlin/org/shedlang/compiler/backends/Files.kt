package org.shedlang.compiler.backends

import java.nio.file.Files.createTempDirectory
import java.nio.file.Path

fun createTempDirectory(): Path {
    return createTempDirectory("shed-")
}
