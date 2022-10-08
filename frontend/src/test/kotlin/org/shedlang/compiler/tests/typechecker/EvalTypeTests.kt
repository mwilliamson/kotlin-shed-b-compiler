package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.ResolvedReferencesMap
import org.shedlang.compiler.TypeCheckError
import org.shedlang.compiler.typechecker.evalType
import org.shedlang.compiler.typechecker.newTypeContext
import org.shedlang.compiler.types.*

class EvalTypeTests {
    @Test
    fun whenReferencedVariableIsNotATypeThenErrorIsThrown() {
        val reference = typeLevelReference("x")

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
        val reference = typeLevelReference("x")
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
        val reference = typeLevelReference("x")

        val type = evalType(
            reference,
            typeContext(referenceTypes = mapOf(reference to IntMetaType))
        )
        assertThat(type, isIntType)
    }

    @Test
    fun typeLevelFieldAccessHasTypeOfField() {
        val moduleReference = typeLevelReference("M")
        val moduleType = moduleType(fields = mapOf(
            "T" to IntMetaType
        ))

        val application = typeLevelFieldAccess(moduleReference, "T")

        val context = typeContext(referenceTypes = mapOf(
            moduleReference to moduleType
        ))
        val type = evalType(application, context)

        assertThat(type, isIntType)
    }

    @Test
    fun canEvaluateFunctionTypeNode() {
        val typeParameter = typeParameter("T")
        val typeParameterReference = typeLevelReference("T")
        val boolReference = typeLevelReference("Bool")
        val intReference = typeLevelReference("Int")
        val effectReference = typeLevelReference("Io")

        val node = functionTypeNode(
            typeLevelParameters = listOf(typeParameter),
            positionalParameters = listOf(typeParameterReference),
            namedParameters = listOf(functionTypeNamedParameter("x", intReference)),
            effect = effectReference,
            returnType = boolReference
        )

        val type = evalType(
            node,
            typeContext(
                referenceTypes = mapOf(
                    boolReference to BoolMetaType,
                    intReference to IntMetaType,
                    effectReference to effectType(IoEffect)
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
        val typeParameterReference = typeLevelReference("T")
        val typeParameter = contravariantTypeParameter("T")

        val node = functionTypeNode(returnType = typeParameterReference)
        val typeContext = typeContext(referenceTypes = mapOf(
            typeParameterReference to metaType(typeParameter)
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
        val boolReference = typeLevelReference("Bool")
        val intReference = typeLevelReference("Int")

        val node = tupleTypeNode(elementTypes = listOf(
            boolReference,
            intReference
        ))

        val type = evalType(
            node,
            typeContext(
                referenceTypes = mapOf(
                    boolReference to BoolMetaType,
                    intReference to IntMetaType
                )
            )
        )
        assertThat(type, isTupleType(elementTypes = isSequence(isBoolType, isIntType)))
    }
}
