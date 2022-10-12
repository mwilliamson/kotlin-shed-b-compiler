package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.Module
import org.shedlang.compiler.frontend.ModuleResult
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ImportPath
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.ModuleNotFoundError
import org.shedlang.compiler.typechecker.MultipleModulesWithSameNameFoundError
import org.shedlang.compiler.typechecker.typeCheckImport

class TypeCheckImportTests {
    @Test
    fun importIntroducesModuleIntoScope() {
        val path = ImportPath.relative(listOf("Messages"))
        val target = targetVariable("M")
        val node = import(
            target = target,
            path = path
        )
        val moduleType = moduleType(fields = mapOf())
        val typeContext = typeContext(
            modules = mapOf(
                path to ModuleResult.Found(Module.Native(
                    type = moduleType,
                    name = identifiers("Messages")
                ))
            )
        )
        typeCheckImport(node, typeContext)
        assertThat(typeContext.typeOf(target), cast(equalTo(moduleType)))
    }

    @Test
    fun whenModuleIsNotFoundThenErrorIsThrown() {
        val path = ImportPath.relative(listOf("Messages"))
        val node = import(name = Identifier("M"), path = path)
        val typeContext = typeContext(modules = mapOf(path to ModuleResult.NotFound(name = identifiers("Lib", "Messages"))))

        assertThat(
            { typeCheckImport(node, typeContext) },
            throwsException(has(ModuleNotFoundError::name, isSequence(isIdentifier("Lib"), isIdentifier("Messages"))))
        )
    }

    @Test
    fun whenMultipleModulesAreNotFoundThenErrorIsThrown() {
        val path = ImportPath.relative(listOf("Messages"))
        val node = import(name = Identifier("M"), path = path)
        val typeContext = typeContext(modules = mapOf(path to ModuleResult.FoundMany(name = identifiers("Lib", "Messages"))))

        assertThat(
            { typeCheckImport(node, typeContext) },
            throwsException(has(MultipleModulesWithSameNameFoundError::name, isSequence(isIdentifier("Lib"), isIdentifier("Messages"))))
        )
    }

    private fun identifiers(vararg names: String) = names.map(::Identifier).toList()
}
