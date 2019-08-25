package org.shedlang.compiler.backends.javascript.tests

import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import org.shedlang.compiler.tests.findRoot
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Path

fun jsFormat(source: String): String {
    if (!inFormatCache(source)) {
        addToFormatCache(source)
    }

    return readFromFormatCache(source)
}

fun saveCache() {
    writeCache(findCachePath(), cache.value)
}

private val cache: Lazy<MutableMap<String, String>> = lazy {
    readCache(findCachePath()).toMutableMap()
}

private fun readCache(cachePath: Path): Map<String, String> {
    return if (cachePath.toFile().exists()) {
        val json = Parser.default().parse(cachePath.toString()) as JsonArray<*>
        json.associate { element ->
            val obj = (element as JsonObject)
            obj.string("unformatted")!! to obj.string("formatted")!!
        }
    } else {
        mapOf<String, String>()
    }
}

private fun writeCache(cachePath: Path, cache: Map<String, String>) {
    val json = JsonArray(cache.map { (unformatted, formatted) ->
        JsonObject(mapOf("unformatted" to unformatted, "formatted" to formatted))
    })

    cachePath.toFile().writeText(json.toJsonString())
}

private fun findCachePath() = findTestUtilsRoot().resolve("cache.json")

private fun inFormatCache(source: String): Boolean {
    return cache.value.containsKey(source)
}

private fun addToFormatCache(source: String) {
    cache.value[source] = jsPrettier(source)
}

private fun readFromFormatCache(source: String): String {
    return cache.value[source]!!
}

private fun jsPrettier(source: String): String {
    val prettierPath = findTestUtilsRoot().resolve("node_modules/.bin/prettier")

    val process = ProcessBuilder(prettierPath.toString(), "--stdin-filepath", "input.js")
        .start()
    process.outputStream.write(source.toByteArray(StandardCharsets.UTF_8))
    process.outputStream.close()
    val exitCode = process.waitFor()
    if (exitCode == 0) {
        return readString(process.inputStream)
    } else {
        val stderr = readString(process.errorStream)
        throw Exception("failed to format\n" + stderr)
    }
}

private fun readString(stream: InputStream): String {
    return InputStreamReader(stream, Charsets.UTF_8).use(InputStreamReader::readText)
}

private fun findTestUtilsRoot(): Path {
    return findRoot().resolve("test-utils")
}
