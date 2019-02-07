package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.typeCheck

class TypeCheckUnionTests {
    @Test
    fun unionDeclaresType() {
        val node = union("X", listOf(
            unionMember(name = "Member1"),
            unionMember(name = "Member2")
        ))


        val typeContext = typeContext(moduleName = listOf("Example"))
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isUnionType(
            name = isIdentifier("X"),
            members = isSequence(
                isShapeType(name = isIdentifier("Member1")),
                isShapeType(name = isIdentifier("Member2"))
            )
        )))
    }

    @Test
    fun unionMembersHaveTagField() {
        val node = union("X", listOf(
            unionMember(name = "Member1"),
            unionMember(name = "Member2")
        ))

        val typeContext = typeContext(
            moduleName = listOf("A", "B")
        )
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isUnionType(
            name = isIdentifier("X"),
            members = isSequence(
                isShapeType(
                    name = isIdentifier("Member1"),
                    fields = isSequence(
                        isField(
                            name = isIdentifier("\$unionTag\$A.B\$X"),
                            isConstant = equalTo(true),
                            type = equalTo(symbolType(
                                module = listOf("A", "B"),
                                name = "@Member1"
                            ))
                        )
                    )
                ),
                isShapeType(
                    name = isIdentifier("Member2"),
                    fields = isSequence(
                        isField(
                            name = isIdentifier("\$unionTag\$A.B\$X"),
                            isConstant = equalTo(true),
                            type = equalTo(symbolType(
                                module = listOf("A", "B"),
                                name = "@Member2"
                            ))
                        )
                    )
                )
            )
        )))
    }

    @Test
    @Disabled("WIP")
    fun unionWithTypeParametersDeclaresTypeFunction() {
//        val typeParameterDeclaration = typeParameter("T")
//        val typeParameterReference = staticReference("T")
//
//        val shapeTypeTypeParameter = invariantTypeParameter("U")
//        val shapeType = parametrizedShapeType("ParametrizedShapeType", parameters = listOf(shapeTypeTypeParameter))
//        val shapeTypeReference = staticReference("ParametrizedShapeType")
//
//        val node = union(
//            "Union",
//            staticParameters = listOf(typeParameterDeclaration),
//            members = listOf(staticApplication(shapeTypeReference, listOf(typeParameterReference)))
//        )
//
//        val typeContext = typeContext(
//            references = mapOf(typeParameterReference to typeParameterDeclaration),
//            referenceTypes = mapOf(shapeTypeReference to MetaType(shapeType))
//        )
//        typeCheck(node, typeContext)
//        assertThat(typeContext.typeOf(node), isMetaType(isTypeFunction(
//            parameters = isSequence(isTypeParameter(name = isIdentifier("T"), variance = isInvariant)),
//            type = isUnionType(
//                name = isIdentifier("Union"),
//                members = isSequence(
//                    isShapeType(staticArguments = isSequence(isTypeParameter(name = isIdentifier("T"), variance = isInvariant)))
//                )
//            )
//        )))
    }
}
