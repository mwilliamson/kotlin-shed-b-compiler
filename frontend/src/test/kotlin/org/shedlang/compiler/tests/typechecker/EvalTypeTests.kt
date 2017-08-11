package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.ResolvedReferencesMap
import org.shedlang.compiler.typechecker.evalType
import org.shedlang.compiler.typechecker.newTypeContext
import org.shedlang.compiler.types.*

class EvalTypeTests {
    @Test
    fun whenReferencedVariableIsNotATypeThenErrorIsThrown() {
        val reference = typeReference("x")

        assertThat(
            { evalType(
                reference,
                typeContext(referenceTypes = mapOf(reference to IntType))
            ) },
            // TODO: should be more like MetaType(Hole)
            throwsUnexpectedType(
                expected = isMetaType(AnyType),
                actual = IntType
            )
        )
    }

    @Test
    fun whenVariableHasNoTypeThenCompilerErrorIsThrown() {
        val reference = typeReference("x")

        assertThat(
            { evalType(
                reference,
                newTypeContext(
                    nodeTypes = mutableMapOf(),
                    resolvedReferences = ResolvedReferencesMap(mapOf(reference.nodeId to freshNodeId())),
                    getModule = { moduleName -> throw UnsupportedOperationException() }
                )
            ) },
            throwsCompilerError("type of x is unknown")
        )
    }

    @Test
    fun typeOfTypeReferenceIsTypeOfMetaType() {
        val reference = typeReference("x")

        val type = evalType(
            reference,
            typeContext(referenceTypes = mapOf(reference to MetaType(IntType)))
        )
        assertThat(type, isIntType)
    }

    @Test
    fun typeApplicationHasTypeOfApplyingType() {
        val listReference = typeReference("Box")
        val boolReference = typeReference("Bool")

        val typeParameter = invariantTypeParameter("T")
        val listType = parametrizedShapeType(
            "Box",
            parameters = listOf(typeParameter),
            fields = mapOf(
                "value" to typeParameter
            )
        )
        val application = typeApplication(listReference, listOf(boolReference))

        val type = evalType(
            application,
            typeContext(referenceTypes = mapOf(
                listReference to MetaType(listType),
                boolReference to MetaType(BoolType)
            ))
        )
        assertThat(type, isShapeType(
            name = equalTo("Box"),
            typeArguments = isSequence(isBoolType),
            fields = listOf(
                "value" to isBoolType
            )
        ))
    }

    @Test
    fun canEvaluateFunctionTypeNode() {
        val intReference = typeReference("Int")
        val boolReference = typeReference("Bool")

        val node = functionType(
            arguments = listOf(intReference),
            returnType = boolReference
        )

        val type = evalType(
            node,
            typeContext(referenceTypes = mapOf(
                intReference to MetaType(IntType),
                boolReference to MetaType(BoolType)
            ))
        )
        assertThat(type, isFunctionType(
            arguments = isSequence(isIntType),
            returnType = isBoolType
        ))
    }

    private fun isMetaType(type: Type): Matcher<Type> {
        return cast(
            has(MetaType::type, equalTo(type))
        )
    }
}
