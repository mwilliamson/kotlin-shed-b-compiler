package org.shedlang.compiler.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.shedlang.compiler.findRoot


class SnapshotterResolver : ParameterResolver {
    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return parameterContext.parameter.type == Snapshotter::class.java
    }

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
        return Snapshotter(uniqueId = extensionContext.uniqueId)
    }
}

class Snapshotter(val uniqueId: String) {
    fun assertSnapshot(actualSnapshot: String) {
        val snapshotDirectory = findRoot().resolve("snapshots")

        val expectedSnapshotPath = snapshotDirectory.resolve(uniqueId + ".expected")
        val expectedSnapshotFile = expectedSnapshotPath.toFile()

        try {
            if (expectedSnapshotFile.exists()) {
                val expectedSnapshot = expectedSnapshotFile.readText()
                assertThat(actualSnapshot, equalTo(expectedSnapshot))
            } else {
                throw AssertionError("snapshot does not exist, got:\n" + actualSnapshot)
            }
        } catch (error: AssertionError) {
            val actualSnapshotPath = snapshotDirectory.resolve(uniqueId + ".actual")
            val actualSnapshotFile = actualSnapshotPath.toFile()
            actualSnapshotFile.parentFile.mkdirs()
            actualSnapshotFile.writeText(actualSnapshot)
            throw error
        }
    }
}
