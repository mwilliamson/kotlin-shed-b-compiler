package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.frontend.tests.isIdentifier
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.MetaType
import org.shedlang.compiler.types.invariantTypeParameter
import org.shedlang.compiler.types.metaTypeToType

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

    @Test
    fun unionAddsTagFieldToShapes() {
        val member1Node = shape(name = "Member1")
        val member2Node = shape(name = "Member2")
        val member1Reference = staticReference("Member1")
        val member2Reference = staticReference("Member2")
        val unionNode = union(name = "X", members = listOf(
            member1Reference,
            member2Reference
        ))

        val typeContext = typeContext(
            moduleName = listOf("A", "B"),
            references = mapOf(
                member1Reference to member1Node,
                member2Reference to member2Node
            )
        )
        typeCheck(
            module(body = listOf(
                member1Node,
                member2Node,
                unionNode
            )),
            typeContext
        )
        val unionType = metaTypeToType(typeContext.typeOf(unionNode))!!
        val member1Type = metaTypeToType(typeContext.typeOf(member1Node))!!
        val member2Type = metaTypeToType(typeContext.typeOf(member2Node))!!

        assertThat(unionType, isUnionType(
            members = isSequence(
                isType(member1Type),
                isType(member2Type)
            )
        ))
        assertThat(member1Type, isShapeType(fields = isSequence(
            isField(
                shapeId = equalTo(unionNode.nodeId),
                name = isIdentifier("\$unionTag\$A.B\$X"),
                isConstant = equalTo(true),
                type = equalTo(symbolType(
                    module = listOf("A", "B"),
                    name = "@Member1"
                ))
            )
        )))
        assertThat(member2Type, isShapeType(fields = isSequence(
            isField(
                shapeId = equalTo(unionNode.nodeId),
                name = isIdentifier("\$unionTag\$A.B\$X"),
                isConstant = equalTo(true),
                type = equalTo(symbolType(
                    module = listOf("A", "B"),
                    name = "@Member2"
                ))
            )
        )))
    }
}
