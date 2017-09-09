package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.testing.*
import org.shedlang.compiler.tests.isBoolType
import org.shedlang.compiler.tests.isFunctionType
import org.shedlang.compiler.tests.isIntType
import org.shedlang.compiler.tests.isShapeType
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
            // TODO: should be more like MetaType(Hole)
            throwsUnexpectedType(
                expected = isMetaType(AnyType),
                actual = IntType
            )
        )
    }

    @Test
    fun whenVariableHasNoTypeThenCompilerErrorIsThrown() {
        val reference = staticReference("x")

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
            fields = mapOf(
                "value" to typeParameter
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
            name = equalTo("Box"),
            typeArguments = isSequence(isBoolType),
            fields = listOf(
                "value" to isBoolType
            )
        ))
    }

    @Test
    fun staticFieldAccessHasTypeOfField() {
        val moduleReference = staticReference("M")
        val moduleType = ModuleType(fields = mapOf(
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
        val intReference = staticReference("Int")
        val boolReference = staticReference("Bool")
        val effectReference = staticReference("Io")

        val node = functionTypeNode(
            arguments = listOf(intReference),
            effects = listOf(effectReference),
            returnType = boolReference
        )

        val type = evalType(
            node,
            typeContext(referenceTypes = mapOf(
                intReference to MetaType(IntType),
                boolReference to MetaType(BoolType),
                effectReference to EffectType(IoEffect)
            ))
        )
        assertThat(type, isFunctionType(
            arguments = isSequence(isIntType),
            effects = equalTo(setOf(IoEffect)),
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

    private fun isMetaType(type: Type): Matcher<Type> {
        return cast(
            has(MetaType::type, equalTo(type))
        )
    }
}
