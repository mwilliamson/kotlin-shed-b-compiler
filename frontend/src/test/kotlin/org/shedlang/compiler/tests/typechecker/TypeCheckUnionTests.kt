package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.TypeCheckError
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.MetaType
import org.shedlang.compiler.types.TagField
import org.shedlang.compiler.types.TagValue
import org.shedlang.compiler.types.invariantTypeParameter

class TypeCheckUnionTests {
    @Test
    fun unionDeclaresType() {
        val tagField = TagField("Tag")
        val member1 = shapeType(name = "Member1", tagValue = TagValue(tagField, freshNodeId()))
        val member2 = shapeType(name = "Member2", tagValue = TagValue(tagField, freshNodeId()))
        val member1Reference = staticReference("Member1")
        val member2Reference = staticReference("Member2")
        val node = union("X", listOf(
            member1Reference,
            member2Reference
        ))


        val typeContext = typeContext(referenceTypes = mapOf(
            member1Reference to MetaType(member1),
            member2Reference to MetaType(member2)
        ))
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isUnionType(
            name = equalTo("X"),
            members = isSequence(isType(member1), isType(member2))
        )))
    }

    @Test
    fun unionWithTypeParametersDeclaresTypeFunction() {
        val typeParameterDeclaration = typeParameter("T")
        val typeParameterReference = staticReference("T")

        val shapeTypeTypeParameter = invariantTypeParameter("U")
        val shapeType = parametrizedShapeType("ParametrizedShapeType", parameters = listOf(shapeTypeTypeParameter))
        val shapeTypeReference = staticReference("ParametrizedShapeType")

        val node = union(
            "Union",
            typeParameters = listOf(typeParameterDeclaration),
            members = listOf(staticApplication(shapeTypeReference, listOf(typeParameterReference)))
        )

        val typeContext = typeContext(
            references = mapOf(typeParameterReference to typeParameterDeclaration),
            referenceTypes = mapOf(shapeTypeReference to MetaType(shapeType))
        )
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isTypeFunction(
            parameters = isSequence(isTypeParameter(name = equalTo("T"), variance = isInvariant)),
            type = isUnionType(
                name = equalTo("Union"),
                members = isSequence(
                    isShapeType(typeArguments = isSequence(isTypeParameter(name = equalTo("T"), variance = isInvariant)))
                )
            )
        )))
    }

    @Test
    fun tagIsGenerated() {
        val node = union("X")

        val typeContext = typeContext()
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isUnionType(
            tagField = isTag(name = equalTo("X"), tagId = equalTo(node.nodeId))
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
