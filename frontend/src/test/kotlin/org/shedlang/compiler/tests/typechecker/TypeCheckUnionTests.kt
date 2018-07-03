package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.frontend.tests.*
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.MetaType
import org.shedlang.compiler.types.invariantTypeParameter

class TypeCheckUnionTests {
    @Test
    fun unionDeclaresType() {
        val member1 = shapeType(name = "Member1")
        val member2 = shapeType(name = "Member2")
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
            name = isIdentifier("X"),
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
            staticParameters = listOf(typeParameterDeclaration),
            members = listOf(staticApplication(shapeTypeReference, listOf(typeParameterReference)))
        )

        val typeContext = typeContext(
            references = mapOf(typeParameterReference to typeParameterDeclaration),
            referenceTypes = mapOf(shapeTypeReference to MetaType(shapeType))
        )
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isTypeFunction(
            parameters = isSequence(isTypeParameter(name = isIdentifier("T"), variance = isInvariant)),
            type = isUnionType(
                name = isIdentifier("Union"),
                members = isSequence(
                    isShapeType(staticArguments = isSequence(isTypeParameter(name = isIdentifier("T"), variance = isInvariant)))
                )
            )
        )))
    }
}
