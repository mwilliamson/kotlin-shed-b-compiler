package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.ResolvedReferencesMap
import org.shedlang.compiler.typechecker.newTypeContext
import org.shedlang.compiler.typechecker.typeCheckTypesModuleStatement
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.MetaType

class TypeCheckValTypeTests {
    @Test
    fun typeExpressionIsTypeChecked() {
        val intReference = staticReference("Int")
        val node = valType(type = intReference)
        val typeContext = typeContext(referenceTypes = mapOf(intReference to IntType))
        assertThat(
            {
                typeCheckTypesModuleStatement(node, typeContext)
            },
            throwsUnexpectedType(
                expected = isMetaTypeGroup,
                actual = isIntType
            )
        )
    }

    @Test
    fun valIsTypedUsingTypeExpression() {
        val intDeclaration = declaration("Int")
        val intReference = staticReference("Int")
        val node = valType(name = "value", type = intReference)
        val typeContext = newTypeContext(
            moduleName = null,
            nodeTypes = mapOf(
                intDeclaration.nodeId to MetaType(IntType)
            ),
            resolvedReferences = ResolvedReferencesMap(mapOf(
                intReference.nodeId to intDeclaration
            )),
            getModule = { moduleName -> throw UnsupportedOperationException() }
        )
        typeCheckTypesModuleStatement(node, typeContext)
        assertThat(typeContext.typeOf(node), isIntType)
    }
}
