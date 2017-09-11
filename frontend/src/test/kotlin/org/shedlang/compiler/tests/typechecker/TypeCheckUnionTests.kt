package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.TypeCheckError
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.BoolType
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.MetaType

class TypeCheckUnionTests {
    @Test
    fun unionDeclaresType() {
        val intType = staticReference("Int")
        val boolType = staticReference("Bool")
        val node = union("X", listOf(
            intType,
            boolType
        ))

        val typeContext = typeContext(referenceTypes = mapOf(
            intType to MetaType(IntType),
            boolType to MetaType(BoolType)
        ))
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isUnionType(
            name = equalTo("X"),
            members = isSequence(isIntType, isBoolType)
        )))
    }

    @Test
    fun unionWithTypeParametersDeclaresTypeFunction() {
        val typeParameterDeclaration = typeParameter("T")
        val typeParameterReference = staticReference("T")
        val node = union(
            "X",
            typeParameters = listOf(typeParameterDeclaration),
            members = listOf(typeParameterReference)
        )

        val typeContext = typeContext(
            references = mapOf(typeParameterReference to typeParameterDeclaration)
        )
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isTypeFunction(
            parameters = isSequence(isTypeParameter(name = equalTo("T"), variance = isInvariant)),
            type = isUnionType(
                name = equalTo("X"),
                members = isSequence(isTypeParameter(name = equalTo("T"), variance = isInvariant))
            )
        )))
    }

    @Test
    fun whenUnionNodeHasNoTagThenTypeHasNoTag() {
        val node = union("X", tag = false)

        val typeContext = typeContext()
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isUnionType(
            tag = absent()
        )))
    }

    @Test
    fun whenUnionNodeHasTagThenTypeHasTag() {
        val node = union("X", tag = true)

        val typeContext = typeContext()
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isUnionType(
            tag = present(isTag(name = equalTo("X"), tagId = equalTo(node.nodeId)))
        )))
    }

    @Test
    fun typeOfUnionIsValidated() {
        val memberReference = staticReference("Member")
        val memberType = shapeType("Member", tagValueFor = null)
        val node = union("U", members = listOf(memberReference))

        val typeContext = typeContext(
            referenceTypes = mapOf(memberReference to MetaType(memberType))
        )

        // TODO: use more specific exception
        assertThat(
            { typeCheck(node, typeContext); typeContext.undefer() },
            throws(has(TypeCheckError::message, equalTo("union members must have tag values")))
        )
    }
}
