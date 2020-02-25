package org.shedlang.compiler.stackinterpreter.tests

import com.natpryce.hamkrest.assertion.assertThat
import org.junit.jupiter.api.Test
import org.shedlang.compiler.Types
import org.shedlang.compiler.TypesMap
import org.shedlang.compiler.ast.BinaryOperator
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.backends.FieldValue
import org.shedlang.compiler.backends.SimpleCodeInspector
import org.shedlang.compiler.backends.tests.StackIrExecutionTests
import org.shedlang.compiler.backends.tests.loader
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.ResolvedReferencesMap
import org.shedlang.compiler.types.*

class InterpreterTests: StackIrExecutionTests(StackInterpreterExecutionEnvironment) {
    @Test
    fun constantSymbolFieldsOnShapesHaveValueSet() {
        val shapeDeclaration = shape("Pair")
        val shapeReference = variableReference("Pair")

        val receiverTarget = targetVariable("receiver")
        val receiverDeclaration = valStatement(
            target = receiverTarget,
            expression = call(
                shapeReference,
                namedArguments = listOf()
            )
        )
        val receiverReference = variableReference("receiver")
        val fieldAccess = fieldAccess(receiverReference, "constantField")

        val symbol = Symbol(listOf(Identifier("A")), "B")
        val inspector = SimpleCodeInspector(
            shapeFields = mapOf(
                shapeDeclaration to listOf(
                    fieldInspector(name = "constantField", value = FieldValue.Symbol(symbol))
                )
            )
        )
        val references = ResolvedReferencesMap(mapOf(
            shapeReference.nodeId to shapeDeclaration,
            receiverReference.nodeId to receiverTarget
        ))
        val shapeType = shapeType()
        val types = createTypes(
            expressionTypes = mapOf(
                receiverReference.nodeId to shapeType,
                shapeReference.nodeId to MetaType(shapeType)
            )
        )

        val loader = loader(inspector = inspector, references = references, types = types)
        val instructions = loader.loadModuleStatement(shapeDeclaration)
            .addAll(loader.loadFunctionStatement(receiverDeclaration))
            .addAll(loader.loadExpression(fieldAccess))
        val value = executeInstructions(instructions)

        assertThat(value, isSymbol(symbol))
    }

    private fun createTypes(
        expressionTypes: Map<Int, Type> = mapOf(),
        targetTypes: Map<Int, Type> = mapOf()
    ): Types {
        return TypesMap(
            discriminators = mapOf(),
            expressionTypes = expressionTypes,
            targetTypes = targetTypes,
            variableTypes = mapOf()
        )
    }
}
