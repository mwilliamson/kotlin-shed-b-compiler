package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.frontend.tests.*
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.FieldAlreadyDeclaredError
import org.shedlang.compiler.typechecker.TypeCheckError
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.*

class TypeCheckShapeTests {
    @Test
    fun shapeDeclaresType() {
        val intType = staticReference("Int")
        val boolType = staticReference("Bool")
        val node = shape("X", fields = listOf(
            shapeField("a", intType),
            shapeField("b", boolType)
        ))

        val typeContext = typeContext(referenceTypes = mapOf(
            intType to MetaType(IntType),
            boolType to MetaType(BoolType)
        ))
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isShapeType(
            name = equalTo("X"),
            fields = listOf("a" to isIntType, "b" to isBoolType)
        )))
    }

    @Test
    fun whenShapeDeclaresMultipleFieldsWithSameNameThenExceptionIsThrown() {
        val intType = staticReference("Int")
        val node = shape("X", fields = listOf(
            shapeField("a", intType),
            shapeField("a", intType)
        ))

        val typeContext = typeContext(referenceTypes = mapOf(
            intType to MetaType(IntType)
        ))

        assertThat(
            { typeCheck(node, typeContext) },
            throws(
                has(FieldAlreadyDeclaredError::fieldName, equalTo("a"))
            )
        )
    }

    @Test
    fun shapeWithTypeParametersDeclaresTypeFunction() {
        val typeParameterDeclaration = typeParameter("T")
        val typeParameterReference = staticReference("T")
        val node = shape(
            "X",
            staticParameters = listOf(typeParameterDeclaration),
            fields = listOf(
                shapeField("a", typeParameterReference)
            )
        )

        val typeContext = typeContext(
            references = mapOf(typeParameterReference to typeParameterDeclaration)
        )
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isTypeFunction(
            parameters = isSequence(isTypeParameter(name = equalTo("T"), variance = isInvariant)),
            type = isShapeType(
                name = equalTo("X"),
                fields = listOf("a" to isTypeParameter(name = equalTo("T"), variance = isInvariant))
            )
        )))
    }

    @Test
    fun typeParameterHasParsedVariance() {
        val typeParameterDeclaration = typeParameter("T", variance = Variance.COVARIANT)
        val typeParameterReference = staticReference("T")
        val node = shape(
            "X",
            staticParameters = listOf(typeParameterDeclaration)
        )

        val typeContext = typeContext(
            references = mapOf(typeParameterReference to typeParameterDeclaration)
        )
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isTypeFunction(
            parameters = isSequence(isTypeParameter(name = equalTo("T"), variance = isCovariant))
        )))
    }

    @Test
    fun whenShapeNodeHasNoTagThenTypeHasNoTag() {
        val node = shape("X", tag = false)

        val typeContext = typeContext()
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isShapeType(
            tagField = absent()
        )))
    }

    @Test
    fun whenShapeNodeHasTagThenTypeHasTag() {
        val node = shape("X", tag = true)

        val typeContext = typeContext()
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isShapeType(
            tagField = present(isTag(name = equalTo("X"), tagId = equalTo(node.nodeId)))
        )))
    }

    @Test
    fun whenShapeNodeHasNoTagValueThenTypeHasNoTagValue() {
        val node = shape("X", hasTagValueFor = null)

        val typeContext = typeContext()
        typeCheck(node, typeContext)
        assertThat(typeContext.typeOf(node), isMetaType(isShapeType(
            tagValue = absent()
        )))
    }

    @Test
    fun whenShapeNodeHasTagValueThenTypeHasTagValue() {
        val taggedReference = staticReference("T")
        val taggedType = unionType("T")

        val node = shape("X", hasTagValueFor = taggedReference)

        val typeContext = typeContext(
            referenceTypes = mapOf(taggedReference to MetaType(taggedType))
        )
        typeCheck(node, typeContext)

        assertThat(typeContext.typeOf(node), isMetaType(isShapeType(
            tagValue = present(isTagValue(
                tagField = equalTo(taggedType.declaredTagField),
                tagValueId = equalTo(node.nodeId)
            ))
        )))
    }

    @Test
    fun tagValueCanReferToTypeFunction() {
        val taggedReference = staticReference("T")
        val tag = TagField("T")
        val taggedType = parametrizedUnionType("T", tagField = tag)

        val node = shape("X", hasTagValueFor = taggedReference)

        val typeContext = typeContext(
            referenceTypes = mapOf(taggedReference to MetaType(taggedType))
        )
        typeCheck(node, typeContext)

        assertThat(typeContext.typeOf(node), isMetaType(isShapeType(
            tagValue = present(isTagValue(
                tagField = equalTo(tag),
                tagValueId = equalTo(node.nodeId)
            ))
        )))
    }

    @Test
    fun typeOfShapeIsValidated() {
        val typeParameterDeclaration = typeParameter("T", variance = Variance.CONTRAVARIANT)
        val typeParameterReference = staticReference("T")
        val unitReference = staticReference("Unit")
        val node = shape(
            "Box",
            staticParameters = listOf(typeParameterDeclaration),
            fields = listOf(
                shapeField(type = typeParameterReference)
            )
        )

        val typeContext = typeContext(
            references = mapOf(typeParameterReference to typeParameterDeclaration),
            referenceTypes = mapOf(unitReference to MetaType(UnitType))
        )
        // TODO: use more specific exception
        assertThat(
            { typeCheck(node, typeContext); typeContext.undefer() },
            throws(has(TypeCheckError::message, equalTo("field type cannot be contravariant")))
        )
    }
}
