package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.tests.isType
import org.shedlang.compiler.tests.moduleName
import org.shedlang.compiler.tests.varargsDeclaration
import org.shedlang.compiler.tests.variableReference
import org.shedlang.compiler.typechecker.typeCheckModuleStatement
import org.shedlang.compiler.types.QualifiedName
import org.shedlang.compiler.types.UnitType
import org.shedlang.compiler.types.VarargsType
import org.shedlang.compiler.types.functionType

class TypeCheckVarargsDeclarationTests {
    @Test
    fun varargsDeclarationDeclaresVarargsFunction() {
        val consReference = variableReference("cons")
        val nilReference = variableReference("nil")
        val node = varargsDeclaration(
            name = "list",
            cons = consReference,
            nil = nilReference
        )
        val consType = functionType()
        val nilType = UnitType

        val typeContext = typeContext(
            moduleName = listOf("Example"),
            referenceTypes = mapOf(
                consReference to consType,
                nilReference to nilType
            )
        )
        typeCheckModuleStatement(node, typeContext)

        assertThat(typeContext.typeOf(node), isType(VarargsType(
            qualifiedName = QualifiedName.topLevelType(listOf("Example"), "list"),
            cons = consType,
            nil = nilType
        )))
    }
}
