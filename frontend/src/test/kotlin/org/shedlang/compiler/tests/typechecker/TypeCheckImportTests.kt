package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.import
import org.shedlang.compiler.typechecker.ModuleType
import org.shedlang.compiler.typechecker.typeCheck

class TypeCheckImportTests {
    @Test
    fun importIntroducesModuleIntoScope() {
        val node = import(".messages")
        val moduleType = ModuleType(fields = mapOf())
        val typeContext = typeContext(modules = mapOf(".messages" to moduleType))
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), cast(equalTo(moduleType)))
    }
}
