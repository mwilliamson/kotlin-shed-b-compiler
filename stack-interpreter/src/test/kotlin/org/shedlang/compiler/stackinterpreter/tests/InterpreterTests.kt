package org.shedlang.compiler.stackinterpreter.tests

import com.natpryce.hamkrest.assertion.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.shedlang.compiler.*
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.FieldValue
import org.shedlang.compiler.backends.SimpleCodeInspector
import org.shedlang.compiler.backends.tests.StackIrExecutionEnvironment
import org.shedlang.compiler.backends.tests.StackIrExecutionTests
import org.shedlang.compiler.backends.tests.loader
import org.shedlang.compiler.stackinterpreter.*
import org.shedlang.compiler.stackir.*
import org.shedlang.compiler.tests.*
import org.shedlang.compiler.typechecker.ResolvedReferencesMap
import org.shedlang.compiler.types.*

private val environment = object: StackIrExecutionEnvironment {
    override fun executeInstructions(instructions: List<Instruction>, type: Type, moduleSet: ModuleSet): IrValue {
        val interpreterValue = executeInstructions(instructions, image = loadModuleSet(moduleSet))
        return interpreterValueToIrValue(interpreterValue)
    }

    private fun interpreterValueToIrValue(interpreterValue: InterpreterValue): IrValue {
        return when (interpreterValue) {
            is InterpreterBool -> IrBool(interpreterValue.value)
            is InterpreterCodePoint -> IrCodePoint(interpreterValue.value)
            is InterpreterInt -> IrInt(interpreterValue.value)
            is InterpreterString -> IrString(interpreterValue.value)
            is InterpreterSymbol -> IrSymbol(interpreterValue.value)
            is InterpreterUnit -> IrUnit
            else -> throw UnsupportedOperationException()
        }
    }
}

class InterpreterTests: StackIrExecutionTests(environment) {
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

    @Test
    fun partialCallCombinesPositionalArguments() {
        val parameter1 = parameter("first")
        val parameter2 = parameter("second")
        val parameterReference1 = variableReference("first")
        val parameterReference2 = variableReference("second")

        val function = function(
            name = "main",
            parameters = listOf(parameter1, parameter2),
            body = listOf(
                expressionStatementReturn(
                    binaryOperation(BinaryOperator.SUBTRACT, parameterReference1, parameterReference2)
                )
            )
        )
        val functionReference = variableReference("main")
        val partialCall = partialCall(
            receiver = functionReference,
            positionalArguments = listOf(literalInt(1))
        )
        val call = call(
            receiver = partialCall,
            positionalArguments = listOf(literalInt(2))
        )
        val references = ResolvedReferencesMap(mapOf(
            functionReference.nodeId to function,
            parameterReference1.nodeId to parameter1,
            parameterReference2.nodeId to parameter2
        ))
        val types = createTypes(
            expressionTypes = mapOf(
                partialCall.nodeId to functionType(),
                parameterReference1.nodeId to IntType
            )
        )

        val loader = loader(references = references, types = types)
        val instructions = loader.loadModuleStatement(function).addAll(loader.loadExpression(call))
        val value = executeInstructions(instructions)

        assertThat(value, isInt(-1))
    }

    @Test
    fun partialCallCombinesNamedArguments() {
        val parameter1 = parameter("first")
        val parameter2 = parameter("second")
        val parameterReference1 = variableReference("first")
        val parameterReference2 = variableReference("second")

        val function = function(
            name = "main",
            namedParameters = listOf(parameter1, parameter2),
            body = listOf(
                expressionStatementReturn(
                    binaryOperation(BinaryOperator.SUBTRACT, parameterReference1, parameterReference2)
                )
            )
        )
        val functionReference = variableReference("main")
        val partialCall = partialCall(
            receiver = functionReference,
            namedArguments = listOf(callNamedArgument("second", literalInt(2)))
        )
        val call = call(
            receiver = partialCall,
            namedArguments = listOf(callNamedArgument("first", literalInt(1)))
        )
        val references = ResolvedReferencesMap(mapOf(
            functionReference.nodeId to function,
            parameterReference1.nodeId to parameter1,
            parameterReference2.nodeId to parameter2
        ))
        val types = createTypes(
            expressionTypes = mapOf(
                partialCall.nodeId to functionType(),
                parameterReference1.nodeId to IntType
            )
        )

        val loader = loader(references = references, types = types)
        val instructions = loader.loadModuleStatement(function).addAll(loader.loadExpression(call))
        val value = executeInstructions(instructions)

        assertThat(value, isInt(-1))
    }

    @Nested
    inner class VarargsTests {
        private val headParameter = parameter("head")
        private val tailParameter = parameter("tail")

        private val headReference = variableReference("head")
        private val tailReference = variableReference("tail")

        private val varargsDeclaration = varargsDeclaration(
            name = "tuple",
            cons = functionExpression(
                parameters = listOf(headParameter, tailParameter),
                body = tupleNode(elements = listOf(headReference, tailReference))
            ),
            nil = literalUnit()
        )

        private val varargsReference = variableReference("tuple")

        private val references = ResolvedReferencesMap(mapOf(
            headReference.nodeId to headParameter,
            tailReference.nodeId to tailParameter,
            varargsReference.nodeId to varargsDeclaration
        ))
        private val types = createTypes(
            expressionTypes = mapOf(varargsReference.nodeId to varargsType())
        )

        @Test
        fun callingVarargsWithNoArgumentsCreatesNil() {
            val call = call(
                receiver = varargsReference,
                positionalArguments = listOf()
            )

            val loader = loader(references = references, types = types)
            val instructions = persistentListOf<Instruction>()
                .addAll(loader.loadModuleStatement(varargsDeclaration))
                .addAll(loader.loadExpression(call))
            val value = executeInstructions(instructions)

            assertThat(value, isUnit)
        }

        @Test
        fun callingVarargsWithOneArgumentsCallsConsOnce() {
            val call = call(
                receiver = varargsReference,
                positionalArguments = listOf(literalInt(42))
            )

            val loader = loader(references = references, types = types)
            val instructions = persistentListOf<Instruction>()
                .addAll(loader.loadModuleStatement(varargsDeclaration))
                .addAll(loader.loadExpression(call))
            val value = executeInstructions(instructions)

            assertThat(value, isTuple(elements = isSequence(
                isInt(42),
                isUnit
            )))
        }

        @Test
        fun callingVarargsWithTwoArgumentsCallsConsTwice() {
            val call = call(
                receiver = varargsReference,
                positionalArguments = listOf(literalInt(42), literalString("hello"))
            )

            val loader = loader(references = references, types = types)
            val instructions = persistentListOf<Instruction>()
                .addAll(loader.loadModuleStatement(varargsDeclaration))
                .addAll(loader.loadExpression(call))
            val value = executeInstructions(instructions)

            assertThat(value, isTuple(elements = isSequence(
                isInt(42),
                isTuple(elements = isSequence(
                    isString("hello"),
                    isUnit
                ))
            )))
        }
    }

    @Test
    fun moduleCanBeImported() {
        val exportingModuleName = listOf(Identifier("Exporting"))
        val exportNode = export("x")
        val valTarget = targetVariable("x")
        val exportingModule = stubbedModule(
            name = exportingModuleName,
            node = module(
                exports = listOf(exportNode),
                body = listOf(
                    valStatement(valTarget, literalInt(42))
                )
            ),
            references = ResolvedReferencesMap(mapOf(
                exportNode.nodeId to valTarget
            ))
        )

        val importingModuleName = listOf(Identifier("Importing"))
        val importTarget = targetVariable("x")
        val importTargetFields = targetFields(listOf(fieldName("x") to importTarget))
        val importNode = import(
            importTargetFields,
            ImportPath.absolute(exportingModuleName.map(Identifier::value))
        )
        val reexportNode = export("x")
        val importingModule = stubbedModule(
            name = importingModuleName,
            node = module(
                imports = listOf(importNode),
                exports = listOf(reexportNode)
            ),
            references = ResolvedReferencesMap(mapOf(
                reexportNode.nodeId to importTarget
            )),
            types = createTypes(targetTypes = mapOf(importTargetFields.nodeId to exportingModule.type))
        )

        val image = loadModuleSet(ModuleSet(listOf(exportingModule, importingModule)))
        val value = executeInstructions(
            persistentListOf(
                ModuleInit(importingModuleName),
                ModuleLoad(importingModuleName),
                FieldAccess(Identifier("x"), receiverType = null)
            ),
            image = image
        )

        assertThat(value, isInt(42))
    }

    @Test
    fun moduleNameIsDefinedInEachModule() {
        val moduleName = listOf(Identifier("One"), Identifier("Two"), Identifier("Three"))
        val moduleNameReference = export("moduleName")
        val references = ResolvedReferencesMap(mapOf(
            moduleNameReference.nodeId to Builtins.moduleName
        ))
        val module = stubbedModule(
            name = moduleName,
            node = module(
                exports = listOf(moduleNameReference)
            ),
            references = references
        )

        val image = loadModuleSet(ModuleSet(listOf(module)))
        val value = executeInstructions(
            persistentListOf(
                ModuleInit(moduleName),
                ModuleLoad(moduleName),
                FieldAccess(Identifier("moduleName"), receiverType = null)
            ),
            image = image
        )

        assertThat(value, isString("One.Two.Three"))
    }

    private fun stubbedModule(
        name: ModuleName,
        node: ModuleNode,
        references: ResolvedReferences = ResolvedReferencesMap.EMPTY,
        types: Types = EMPTY_TYPES
    ): Module.Shed {
        return Module.Shed(
            name = name,
            type = ModuleType(mapOf()),
            types = types,
            references = references,
            node = node
        )
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
