package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.TypeCheckError
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.BoolType
import org.shedlang.compiler.types.IntType
import org.shedlang.compiler.types.MetaType
import org.shedlang.compiler.types.TagField

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
    fun whenUnionNodeIsTaggedThenTypeHasNewTag() {
        val node = union("X", tagged = true, superType = null)

        val typeContext = typeContext()
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isUnionType(
            tagField = isTag(name = equalTo("X"), tagId = equalTo(node.nodeId))
        )))
    }

    @Test
    fun whenUnionNodeHasNoSuperTagAndIsNotTaggedThenErrorIsThrown() {
        val node = union("X", tagged = false, superType = null)

        val typeContext = typeContext()
        assertThat(
            { typeCheck(node, typeContext) },
            throws(has(TypeCheckError::message, equalTo("Union is missing tag")))
        )
    }

    @Test
    fun whenUnionNodeHasTaggedSuperTypeThenTypeHasTag() {
        val baseReference = staticReference("Base")
        val tag = TagField("BaseTag")
        val baseType = shapeType(tagField = tag)

        val node = union("X", tagged = false, superType = baseReference)

        val typeContext = typeContext(
            referenceTypes = mapOf(
                baseReference to MetaType(baseType)
            )
        )
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isUnionType(
            tagField = isTag(name = equalTo("BaseTag"), tagId = equalTo(tag.tagFieldId))
        )))
    }

    @Test
    fun typeOfUnionIsValidated() {
        val memberReference = staticReference("Member")
        val memberType = shapeType("Member", tagValue = null)
        val node = union("U", members = listOf(memberReference))

        val typeContext = typeContext(
            referenceTypes = mapOf(memberReference to MetaType(memberType))
        )

        // TODO: use more specific exception
        assertThat(
            { typeCheck(node, typeContext); typeContext.undefer() },
            throws(has(TypeCheckError::message, equalTo("union member did not have tag value for U")))
        )
    }
}
