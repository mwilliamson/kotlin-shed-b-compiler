package org.shedlang.compiler.backends

fun readResourceText(name: String): String {
    val classLoader = ClassLoader.getSystemClassLoader()
    return classLoader.getResourceAsStream(name).use { stream ->
        stream.reader(Charsets.UTF_8).readText()
    }
}
