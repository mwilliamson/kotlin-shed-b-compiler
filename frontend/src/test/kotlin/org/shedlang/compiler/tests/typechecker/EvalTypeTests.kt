package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.frontend.tests.*
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.ResolvedReferencesMap
import org.shedlang.compiler.typechecker.TypeCheckError
import org.shedlang.compiler.typechecker.evalType
import org.shedlang.compiler.typechecker.newTypeContext
import org.shedlang.compiler.types.*

class EvalTypeTests {
    @Test
    fun whenReferencedVariableIsNotATypeThenErrorIsThrown() {
        val reference = staticReference("x")

        assertThat(
            { evalType(
                reference,
                typeContext(referenceTypes = mapOf(reference to IntType))
            ) },
            throwsUnexpectedType(
                expected = isMetaTypeGroup,
                actual = IntType
            )
        )
    }

    @Test
    fun whenVariableHasNoTypeThenCompilerErrorIsThrown() {
        val reference = staticReference("x")
        val declaration = variableBinder("x")

        assertThat(
            { evalType(
                reference,
                newTypeContext(
                    moduleName = null,
                    nodeTypes = mutableMapOf(),
                    resolvedReferences = ResolvedReferencesMap(mapOf(reference.nodeId to declaration)),
                    getModule = { moduleName -> throw UnsupportedOperationException() }
                )
            ) },
            throwsCompilerError("type of x is unknown")
        )
    }

    @Test
    fun typeOfTypeReferenceIsTypeOfMetaType() {
        val reference = staticReference("x")

        val type = evalType(
            reference,
            typeContext(referenceTypes = mapOf(reference to MetaType(IntType)))
        )
        assertThat(type, isIntType)
    }

    @Test
    fun typeApplicationHasTypeOfApplyingType() {
        val listReference = staticReference("Box")
        val boolReference = staticReference("Bool")

        val typeParameter = invariantTypeParameter("T")
        val listType = parametrizedShapeType(
            "Box",
            parameters = listOf(typeParameter),
            fields = listOf(
                field("value", typeParameter)
            )
        )
        val application = staticApplication(listReference, listOf(boolReference))

        val type = evalType(
            application,
            typeContext(referenceTypes = mapOf(
                listReference to MetaType(listType),
                boolReference to MetaType(BoolType)
            ))
        )
        assertThat(type, isShapeType(
            name = isIdentifier("Box"),
            staticArguments = isSequence(isBoolType),
            fields = isSequence(
                isField(name = isIdentifier("value"), type = isBoolType)
            )
        ))
    }

    @Test
    fun staticFieldAccessHasTypeOfField() {
        val moduleReference = staticReference("M")
        val moduleType = moduleType(fields = mapOf(
            "T" to MetaType(IntType)
        ))

        val application = staticFieldAccess(moduleReference, "T")

        val context = typeContext(referenceTypes = mapOf(
            moduleReference to moduleType
        ))
        val type = evalType(application, context)

        assertThat(type, isIntType)
    }

    @Test
    fun canEvaluateFunctionTypeNode() {
        val typeParameter = typeParameter("T")
        val typeParameterReference = staticReference("T")
        val boolReference = staticReference("Bool")
        val intReference = staticReference("Int")
        val effectReference = staticReference("Io")

        val node = functionTypeNode(
            staticParameters = listOf(typeParameter),
            positionalParameters = listOf(typeParameterReference),
            namedParameters = listOf(parameter("x", intReference)),
            effects = listOf(effectReference),
            returnType = boolReference
        )

        val type = evalType(
            node,
            typeContext(
                referenceTypes = mapOf(
                    boolReference to MetaType(BoolType),
                    intReference to MetaType(IntType),
                    effectReference to EffectType(IoEffect)
                ),
                references = mapOf(typeParameterReference to typeParameter)
            )
        )
        assertThat(type, isFunctionType(
            positionalParameters = isSequence(isTypeParameter(name = isIdentifier("T"), variance = isInvariant)),
            namedParameters = isMap(Identifier("x") to isIntType),
            effect = equalTo(IoEffect),
            returnType = isBoolType
        ))
    }

    @Test
    fun functionTypeIsValidated() {
        val typeParameterReference = staticReference("T")
        val typeParameter = contravariantTypeParameter("T")

        val node = functionTypeNode(returnType = typeParameterReference)
        val typeContext = typeContext(referenceTypes = mapOf(
            typeParameterReference to MetaType(typeParameter)
        ))

        assertThat(
            { evalType(node, typeContext) },
            throws(has(TypeCheckError::message, equalTo("return type cannot be contravariant")))
        )
    }

    @Test
    fun canEvaluateEmptyTupleTypeNode() {
        val node = tupleTypeNode(elementTypes = listOf())

        val type = evalType(
            node,
            typeContext()
        )
        assertThat(type, isTupleType(elementTypes = isSequence()))
    }

    @Test
    fun canEvaluateTupleTypeNodeWithElements() {
        val boolReference = staticReference("Bool")
        val intReference = staticReference("Int")

        val node = tupleTypeNode(elementTypes = listOf(
            boolReference,
            intReference
        ))

        val type = evalType(
            node,
            typeContext(
                referenceTypes = mapOf(
                    boolReference to MetaType(BoolType),
                    intReference to MetaType(IntType)
                )
            )
        )
        assertThat(type, isTupleType(elementTypes = isSequence(isBoolType, isIntType)))
    }
}
