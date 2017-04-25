package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.ImportPath
import org.shedlang.compiler.tests.import
import org.shedlang.compiler.typechecker.ModuleType
import org.shedlang.compiler.typechecker.typeCheck

class TypeCheckImportTests {
    @Test
    fun importIntroducesModuleIntoScope() {
        val path = ImportPath.relative(listOf("messages"))
        val node = import(path)
        val moduleType = ModuleType(fields = mapOf())
        val typeContext = typeContext(modules = mapOf(path to moduleType))
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), cast(equalTo(moduleType)))
    }
}
