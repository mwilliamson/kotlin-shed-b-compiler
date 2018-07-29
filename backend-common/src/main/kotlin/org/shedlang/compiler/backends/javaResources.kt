package org.shedlang.compiler.backends

import java.io.InputStream

fun resourceStream(name: String): InputStream {
    val classLoader = ClassLoader.getSystemClassLoader()
    return classLoader.getResourceAsStream(name)
}

fun readResourceText(name: String): String {
    return resourceStream(name).use { stream ->
        stream.reader(Charsets.UTF_8).readText()
    }
}
