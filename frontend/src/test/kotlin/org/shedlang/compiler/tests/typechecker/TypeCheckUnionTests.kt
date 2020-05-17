package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.present
import org.junit.jupiter.api.Test
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.typeCheckModuleStatement

class TypeCheckUnionTests {
    @Test
    fun unionDeclaresType() {
        val node = union("X", listOf(
            unionMember(name = "Member1"),
            unionMember(name = "Member2")
        ))


        val typeContext = typeContext(moduleName = listOf("Example"))
        typeCheckModuleStatement(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isUnionType(
            name = isIdentifier("X"),
            tag = isTag(listOf("Example"), "X"),
            members = isSequence(
                isShapeType(name = isIdentifier("Member1")),
                isShapeType(name = isIdentifier("Member2"))
            )
        )))
    }

    @Test
    fun unionMembersHaveTagValue() {
        val node = union("X", listOf(
            unionMember(name = "Member1"),
            unionMember(name = "Member2")
        ))

        val typeContext = typeContext(
            moduleName = listOf("A", "B")
        )
        typeCheckModuleStatement(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isUnionType(
            name = isIdentifier("X"),
            members = isSequence(
                isShapeType(
                    name = isIdentifier("Member1"),
                    tagValue = present(isTagValue(isTag(listOf("A", "B"), "X"), "Member1")),
                    fields = isSequence()
                ),
                isShapeType(
                    name = isIdentifier("Member2"),
                    tagValue = present(isTagValue(isTag(listOf("A", "B"), "X"), "Member2")),
                    fields = isSequence()
                )
            )
        )))
    }

    @Test
    fun unionWithTypeParametersDeclaresTypeFunction() {
        val typeParameterDeclaration1 = typeParameter("T1")
        val typeParameterDeclaration2 = typeParameter("T2")

        val node = union(
            "Union",
            staticParameters = listOf(typeParameterDeclaration1, typeParameterDeclaration2),
            members = listOf(
                unionMember(name = "Member1", staticParameters = listOf(typeParameterDeclaration1)),
                unionMember(name = "Member2", staticParameters = listOf(typeParameterDeclaration2)),
                unionMember(name = "Member3", staticParameters = listOf())
            )
        )

        val typeContext = typeContext(moduleName = listOf("Example"))
        typeCheckModuleStatement(node, typeContext)
        assertThat(typeContext.typeOf(node), isStaticValueType(isParameterizedStaticValue(
            parameters = isSequence(
                isTypeParameter(name = isIdentifier("T1"), variance = isInvariant),
                isTypeParameter(name = isIdentifier("T2"), variance = isInvariant)
            ),
            value = isUnionType(
                name = isIdentifier("Union"),
                members = isSequence(
                    isShapeType(
                        name = isIdentifier("Member1"),
                        staticParameters = isSequence(
                            isTypeParameter(name = isIdentifier("T1"), variance = isInvariant)
                        ),
                        staticArguments = isSequence(
                            isTypeParameter(name = isIdentifier("T1"), variance = isInvariant)
                        )
                    ),

                    isShapeType(
                        name = isIdentifier("Member2"),
                        staticParameters = isSequence(
                            isTypeParameter(name = isIdentifier("T2"), variance = isInvariant)
                        ),
                        staticArguments = isSequence(
                            isTypeParameter(name = isIdentifier("T2"), variance = isInvariant)
                        )
                    ),

                    isShapeType(
                        name = isIdentifier("Member3"),
                        staticParameters = isSequence(),
                        staticArguments = isSequence()
                    )
                )
            )
        )))
    }
}
