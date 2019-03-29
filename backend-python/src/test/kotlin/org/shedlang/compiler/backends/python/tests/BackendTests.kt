package org.shedlang.compiler.backends.python.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.EMPTY_TYPES
import org.shedlang.compiler.Module
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.backends.python.shedModuleNameToPythonModuleName
import org.shedlang.compiler.backends.python.topLevelPythonPackageName
import org.shedlang.compiler.tests.module
import org.shedlang.compiler.tests.moduleType
import org.shedlang.compiler.typechecker.ResolvedReferencesMap

class BackendTests {
    @Test
    fun moduleNameIsConvertedToModuleUnderShedPackage() {
        val pythonName = shedModuleNameToPythonModuleName(
            moduleName = listOf(Identifier("X"), Identifier("Y")),
            moduleSet = moduleSetWithNames(listOf(listOf("X", "Y")))
        )
        assertThat(pythonName, equalTo(listOf(topLevelPythonPackageName, "X", "Y")))
    }

    @Test
    fun packageIsConvertedToInitModuleUnderShedPackage() {
        val pythonName = shedModuleNameToPythonModuleName(
            moduleName = listOf(Identifier("X"), Identifier("Y")),
            moduleSet = moduleSetWithNames(listOf(listOf("X", "Y", "Z")))
        )
        assertThat(pythonName, equalTo(listOf(topLevelPythonPackageName, "X", "Y", "__init__")))
    }

    private fun moduleSetWithNames(moduleNames: List<List<String>>): ModuleSet {
        return ModuleSet(modules = moduleNames.map { moduleName ->
            Module.Shed(
                name = moduleName.map(::Identifier),
                node = module(),
                references = ResolvedReferencesMap.EMPTY,
                type = moduleType(),
                types = EMPTY_TYPES
            )
        })
    }
}
