package org.shedlang.compiler.stackir

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import org.shedlang.compiler.*
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.CodeInspector
import org.shedlang.compiler.backends.ModuleCodeInspector
import org.shedlang.compiler.backends.fieldArgumentsToFieldsProvided
import org.shedlang.compiler.types.*

class Image internal constructor(private val modules: Map<ModuleName, PersistentList<Instruction>>) {
    companion object {
        val EMPTY = Image(modules = persistentMapOf())
    }

    fun moduleInitialisation(name: ModuleName): List<Instruction>? {
        return modules[name]
    }
}

fun loadModuleSet(moduleSet: ModuleSet): Image {
    return Image(moduleSet.modules.filterIsInstance<Module.Shed>().associate { module ->
        module.name to loadModule(module, moduleSet = moduleSet)
    })
}

private fun loadModule(module: Module.Shed, moduleSet: ModuleSet): PersistentList<Instruction> {
    val loader = Loader(
        inspector = ModuleCodeInspector(module),
        references = module.references,
        types = module.types,
        moduleSet = moduleSet
    )
    return removeUnreachableCode(loader.loadModule(module))
}

class Loader(
    private val references: ResolvedReferences,
    private val types: Types,
    private val inspector: CodeInspector,
    private val moduleSet: ModuleSet
) {
    internal fun loadModule(module: Module.Shed): PersistentList<Instruction> {
        val moduleNameInstructions = if (isReferenced(module, Builtins.moduleName)) {
            persistentListOf(
                PushValue(IrString(module.name.joinToString(".") { part -> part.value })),
                LocalStore(Builtins.moduleName)
            )
        } else {
            persistentListOf()
        }

        val importInstructions = module.node.imports
            .filter { import ->
                import.target.variableBinders().any { variableBinder ->
                    isReferenced(module, variableBinder)
                }
            }
            .flatMap { import ->
                val importedModuleName = resolveImport(module.name, import.path)
                persistentListOf(
                    ModuleInit(importedModuleName),
                    ModuleLoad(importedModuleName)
                ).addAll(loadTarget(import.target))
            }
            .toPersistentList()

        return importInstructions
            .addAll(moduleNameInstructions)
            .addAll(module.node.body.flatMap { statement ->
                loadModuleStatement(statement)
            })
            .add(ModuleStore(
                moduleName = module.name,
                exports = module.node.exports.map { export ->
                    export.name to module.references[export].nodeId
                }
            ))
            .add(ModuleInitExit)
    }

    private fun isReferenced(module: Module.Shed, variableBinder: VariableBindingNode) =
        module.references.referencedNodes.contains(variableBinder)

    fun loadExpression(expression: ExpressionNode): PersistentList<Instruction> {
        return expression.accept(object : ExpressionNode.Visitor<PersistentList<Instruction>> {
            override fun visit(node: UnitLiteralNode): PersistentList<Instruction> {
                val push = PushValue(IrUnit)
                return persistentListOf(push)
            }

            override fun visit(node: BooleanLiteralNode): PersistentList<Instruction> {
                val push = PushValue(IrBool(node.value))
                return persistentListOf(push)
            }

            override fun visit(node: IntegerLiteralNode): PersistentList<Instruction> {
                val push = PushValue(IrInt(node.value))
                return persistentListOf(push)
            }

            override fun visit(node: StringLiteralNode): PersistentList<Instruction> {
                val push = PushValue(IrString(node.value))
                return persistentListOf(push)
            }

            override fun visit(node: UnicodeScalarLiteralNode): PersistentList<Instruction> {
                val push = PushValue(IrUnicodeScalar(node.value))
                return persistentListOf(push)
            }

            override fun visit(node: TupleNode): PersistentList<Instruction> {
                val elementInstructions = node.elements.flatMap { element -> loadExpression(element) }
                return elementInstructions.toPersistentList().add(TupleCreate(node.elements.size))
            }

            override fun visit(node: ReferenceNode): PersistentList<Instruction> {
                return persistentListOf(LocalLoad(resolveReference(node)))
            }

            override fun visit(node: UnaryOperationNode): PersistentList<Instruction> {
                val operandInstructions = loadExpression(node.operand)

                val operationInstruction = when (node.operator) {
                    UnaryOperator.NOT -> BoolNot
                    UnaryOperator.MINUS -> IntMinus
                }

                return operandInstructions.add(operationInstruction)
            }

            override fun visit(node: BinaryOperationNode): PersistentList<Instruction> {
                val left = loadExpression(node.left)
                val right = loadExpression(node.right)
                val operation = when (node.operator) {
                    BinaryOperator.ADD -> when (types.typeOfExpression(node.left)) {
                        IntType -> IntAdd
                        StringType -> StringAdd
                        else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                    }
                    BinaryOperator.AND -> when (types.typeOfExpression(node.left)) {
                        BoolType -> {
                            val endLabel = createLabel()
                            val rightLabel = createLabel()
                            val leftInstructions = loadExpression(node.left)
                            val rightInstructions = loadExpression(node.right)
                            return leftInstructions
                                .add(JumpIfTrue(rightLabel.value, endLabel = endLabel.value))
                                .add(PushValue(IrBool(false)))
                                .add(JumpEnd(endLabel.value))
                                .add(rightLabel)
                                .addAll(rightInstructions)
                                .add(JumpEnd(endLabel.value))
                                .add(endLabel)
                        }
                        else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                    }
                    BinaryOperator.SUBTRACT -> IntSubtract
                    BinaryOperator.MULTIPLY -> IntMultiply
                    BinaryOperator.DIVIDE -> IntDivide
                    BinaryOperator.EQUALS -> when (types.typeOfExpression(node.left)) {
                        BoolType -> BoolEquals
                        UnicodeScalarType -> UnicodeScalarEquals
                        IntType -> IntEquals
                        StringType -> StringEquals
                        else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                    }
                    BinaryOperator.GREATER_THAN -> when (types.typeOfExpression(node.left)) {
                        IntType -> IntGreaterThan
                        UnicodeScalarType -> UnicodeScalarGreaterThan
                        else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                    }
                    BinaryOperator.GREATER_THAN_OR_EQUAL -> when (types.typeOfExpression(node.left)) {
                        IntType -> IntGreaterThanOrEqual
                        UnicodeScalarType -> UnicodeScalarGreaterThanOrEqual
                        else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                    }
                    BinaryOperator.LESS_THAN -> when (types.typeOfExpression(node.left)) {
                        IntType -> IntLessThan
                        UnicodeScalarType -> UnicodeScalarLessThan
                        else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                    }
                    BinaryOperator.LESS_THAN_OR_EQUAL -> when (types.typeOfExpression(node.left)) {
                        IntType -> IntLessThanOrEqual
                        UnicodeScalarType -> UnicodeScalarLessThanOrEqual
                        else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                    }
                    BinaryOperator.NOT_EQUAL -> when (types.typeOfExpression(node.left)) {
                        BoolType -> BoolNotEqual
                        UnicodeScalarType -> UnicodeScalarNotEqual
                        IntType -> IntNotEqual
                        StringType -> StringNotEqual
                        else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                    }
                    BinaryOperator.OR -> when (types.typeOfExpression(node.left)) {
                        BoolType -> {
                            val endLabel = createLabel()
                            val rightLabel = createLabel()
                            val leftInstructions = loadExpression(node.left)
                            val rightInstructions = loadExpression(node.right)
                            return leftInstructions
                                .add(JumpIfFalse(rightLabel.value, endLabel = endLabel.value))
                                .add(PushValue(IrBool(true)))
                                .add(JumpEnd(endLabel.value))
                                .add(rightLabel)
                                .addAll(rightInstructions)
                                .add(JumpEnd(endLabel.value))
                                .add(endLabel)
                        }
                        else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                    }
                    else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                }
                return left.addAll(right).add(operation)
            }

            override fun visit(node: IsNode): PersistentList<Instruction> {
                val expressionInstructions = loadExpression(node.expression)

                val discriminator = inspector.discriminatorForIsExpression(node)

                return expressionInstructions.addAll(typeConditionInstructions(discriminator))
            }

            override fun visit(node: CallNode): PersistentList<Instruction> {
                if (types.typeOfExpression(node.receiver) is VarargsType) {
                    return loadExpression(node.receiver)
                        .addAll(node.positionalArguments.flatMap { argument ->
                            persistentListOf<Instruction>()
                                .add(Duplicate)
                                .add(varargsAccessCons())
                                .add(Swap)
                                .addAll(loadExpression(argument))
                                .add(Swap)
                        })
                        .add(varargsAccessNil())
                        .addAll((0 until node.positionalArguments.size).map {
                            Call(
                                positionalArgumentCount = 2,
                                namedArgumentNames = listOf()
                            )
                        })
                } else {
                    val receiverInstructions = loadExpression(node.receiver)
                    val (argumentInstructions, namedArgumentNames) = loadArguments(node)
                    val call = Call(
                        positionalArgumentCount = node.positionalArguments.size,
                        namedArgumentNames = namedArgumentNames,
                    )
                    return receiverInstructions.addAll(argumentInstructions).add(call)
                }
            }

            override fun visit(node: PartialCallNode): PersistentList<Instruction> {
                val partialFunctionType = types.typeOfExpression(node) as FunctionType

                val receiverVariable = DefineFunction.Parameter(name = Identifier("receiver"), variableId = freshNodeId())
                val receiverInstructions = loadExpression(node.receiver)
                    .add(LocalStore(receiverVariable))

                val positionalArgumentVariables = (0 until node.positionalArguments.size).map { argumentIndex ->
                    DefineFunction.Parameter(name = Identifier("arg_$argumentIndex"), variableId = freshNodeId())
                }
                val positionalArgumentInstructions = node.positionalArguments.zip(positionalArgumentVariables) { argument, variable ->
                    loadExpression(argument).add(LocalStore(variable))
                }.flatten()

                val positionalParameterVariables = (0 until partialFunctionType.positionalParameters.size).map { parameterIndex ->
                    DefineFunction.Parameter(
                        name = Identifier("arg_${node.positionalArguments.size + parameterIndex}"),
                        variableId = freshNodeId()
                    )
                }

                // TODO: Handle splat
                val namedArgumentVariables = node.fieldArguments.map { argument ->
                    DefineFunction.Parameter(name = (argument as FieldArgumentNode.Named).name, variableId = freshNodeId())
                }
                val namedArgumentInstructions = node.fieldArguments.zip(namedArgumentVariables) { argument, variable ->
                    loadExpression((argument as FieldArgumentNode.Named).expression).add(LocalStore(variable))
                }.flatten()

                val namedParameterNames = partialFunctionType.namedParameters.keys.toList()
                val namedParameterVariables = namedParameterNames.map { parameterName ->
                    DefineFunction.Parameter(name = parameterName, variableId = freshNodeId())
                }

                val callVariables = persistentListOf(receiverVariable)
                    .addAll(positionalArgumentVariables)
                    .addAll(positionalParameterVariables)
                    .addAll(namedArgumentVariables)
                    .addAll(namedParameterVariables)

                val createPartial = listOf(
                    DefineFunction(
                        name = "partial",
                        positionalParameters = positionalParameterVariables,
                        namedParameters = namedParameterVariables,
                        bodyInstructions = persistentListOf<Instruction>()
                            .addAll(callVariables.map { variable ->
                                LocalLoad(variable)
                            })
                            .add(Call(
                                positionalArgumentCount = node.positionalArguments.size + partialFunctionType.positionalParameters.size,
                                namedArgumentNames = node.fieldArguments.map { argument -> (argument as FieldArgumentNode.Named).name } + namedParameterNames
                            ))
                            .add(Return)
                    )
                )
                return receiverInstructions
                    .addAll(positionalArgumentInstructions)
                    .addAll(namedArgumentInstructions)
                    .addAll(createPartial)
            }

            override fun visit(node: StaticCallNode): PersistentList<Instruction> {
                return loadExpression(node.receiver)
            }

            override fun visit(node: FieldAccessNode): PersistentList<Instruction> {
                val receiverInstructions = loadExpression(node.receiver)
                val fieldAccess = FieldAccess(
                    fieldName = node.fieldName.identifier,
                    receiverType = types.typeOfExpression(node.receiver)
                )
                return receiverInstructions.add(fieldAccess)
            }

            override fun visit(node: FunctionExpressionNode): PersistentList<Instruction> {
                return persistentListOf(loadFunctionValue(node))
            }

            override fun visit(node: IfNode): PersistentList<Instruction> {
                val conditionInstructions = node.conditionalBranches.map { branch ->
                    loadExpression(branch.condition)
                }

                return generateBranches(
                    conditionInstructions = conditionInstructions,
                    conditionalBodies = node.conditionalBranches.map { branch -> loadBlock(branch.body) },
                    elseBranch = loadBlock(node.elseBranch),
                )
            }

            override fun visit(node: WhenNode): PersistentList<Instruction> {
                val expressionVariableId = freshNodeId()
                val expressionName = Identifier("whenExpression")
                val expressionInstructions = loadExpression(node.expression)
                    .add(LocalStore(variableId = expressionVariableId, name = expressionName))

                val loadExpression = LocalLoad(variableId = expressionVariableId, name = expressionName)

                val conditionInstructions = node.conditionalBranches.map { branch ->
                    val discriminator = inspector.discriminatorForWhenBranch(node, branch)

                    persistentListOf<Instruction>(loadExpression)
                        .addAll(typeConditionInstructions(discriminator))
                }

                val elseBranch = node.elseBranch
                return expressionInstructions.addAll(generateBranches(
                    conditionInstructions = conditionInstructions,
                    conditionalBodies = node.conditionalBranches.map { branch ->
                        persistentListOf<Instruction>(loadExpression)
                            .addAll(loadTarget(branch.target))
                            .addAll(loadBlock(branch.body))
                    },
                    elseBranch = if (elseBranch == null) null else loadBlock(elseBranch),
                ))
            }

            override fun visit(node: HandleNode): PersistentList<Instruction> {
                val effect = (types.typeOfStaticExpression(node.effect) as StaticValueType).value as UserDefinedEffect

                val initialState = node.initialState
                val stateInstructions = if (initialState == null) {
                    persistentListOf<Instruction>()
                } else {
                    loadExpression(initialState)
                }

                val operationHandlerInstructions = node.handlers
                    .map { handler -> loadFunctionValue(handler.function) }

                val body = loadBlock(node.body)
                val effectHandleInstruction = EffectHandle(
                    effect = effect,
                    instructions = body,
                    hasState = initialState != null,
                )

                return persistentListOf<Instruction>()
                    .addAll(stateInstructions)
                    .addAll(operationHandlerInstructions)
                    .add(effectHandleInstruction)
            }

            private fun generateBranches(
                conditionInstructions: List<PersistentList<Instruction>>,
                conditionalBodies: List<PersistentList<Instruction>>,
                elseBranch: PersistentList<Instruction>?,
            ): PersistentList<Instruction> {
                val instructions = mutableListOf<Instruction>()

                val branchBodies = conditionalBodies + elseBranch.nullableToList()
                val conditionLabels = branchBodies.map { createLabel() }
                val endLabel = createLabel()

                conditionInstructions.forEachIndexed { branchIndex, _ ->
                    if (branchIndex > 0) {
                        instructions.add(conditionLabels[branchIndex])
                    }
                    val nextConditionLabel = conditionLabels.getOrNull(branchIndex + 1)
                    if (nextConditionLabel != null) {
                        instructions.addAll(conditionInstructions[branchIndex])
                        instructions.add(JumpIfFalse(nextConditionLabel.value, endLabel = endLabel.value))
                    }
                    instructions.addAll(conditionalBodies[branchIndex])
                    instructions.add(JumpEnd(endLabel.value))
                }

                if (elseBranch != null) {
                    instructions.add(conditionLabels.last())
                    instructions.addAll(elseBranch)
                    instructions.add(JumpEnd(endLabel.value))
                }
                instructions.add(endLabel)
                return instructions.toPersistentList()
            }
        })
    }

    private fun loadArguments(node: CallBaseNode): Pair<List<Instruction>, List<Identifier>> {
        val providesFields = fieldArgumentsToFieldsProvided(node.fieldArguments, typeOfExpression = types::typeOfExpression)

        val namedArgumentNames = mutableListOf<Identifier>()
        val instructions =  node.positionalArguments.flatMap { argument ->
            loadExpression(argument)
        } + node.fieldArguments.zip(providesFields).flatMap { (argument, argumentProvidesFields) ->
            when (argument) {
                is FieldArgumentNode.Named -> {
                    val expressionInstructions = loadExpression(argument.expression)
                    if (argumentProvidesFields.contains(argument.name)) {
                        namedArgumentNames.add(argument.name)
                        expressionInstructions
                    } else {
                        expressionInstructions.add(Discard)
                    }
                }
                is FieldArgumentNode.Splat -> {
                    val expressionInstructions = loadExpression(argument.expression)

                    namedArgumentNames.addAll(argumentProvidesFields)
                    expressionInstructions
                        .addAll(argumentProvidesFields.flatMap { fieldName ->
                            listOf(
                                Duplicate,
                                FieldAccess(fieldName = fieldName, receiverType = types.typeOfExpression(argument.expression)),
                                Swap
                            )
                        })
                        .add(Discard)
                }
            }
        }
        return Pair(instructions, namedArgumentNames)
    }

    fun loadBlock(block: Block): PersistentList<Instruction> {
        val statementInstructions = block.statements.flatMap { statement ->
            loadFunctionStatement(statement)
        }.toPersistentList()

        return if (block.isTerminated) {
            statementInstructions
        } else {
            statementInstructions.add(PushValue(IrUnit))
        }
    }

    fun loadFunctionStatement(statement: FunctionStatementNode): PersistentList<Instruction> {
        return statement.accept(object : FunctionStatementNode.Visitor<PersistentList<Instruction>> {
            override fun visit(node: ExpressionStatementNode): PersistentList<Instruction> {
                val expressionInstructions = loadExpression(node.expression)
                when (node.type) {
                    ExpressionStatementNode.Type.VALUE->
                        return expressionInstructions

                    ExpressionStatementNode.Type.TAILREC -> {
                        // TODO: ensure that this is always valid (probably in the front-end rather than here)
                        val call = expressionInstructions.last() as Call
                        return expressionInstructions
                            .removeAt(expressionInstructions.lastIndex)
                            .add(call.copy(tail = true))
                            .add(Return)
                    }

                    ExpressionStatementNode.Type.NO_VALUE ->
                        return expressionInstructions.add(Discard)

                    ExpressionStatementNode.Type.EXIT ->
                        return expressionInstructions
                            .add(Exit)
                }
            }

            override fun visit(node: ResumeNode): PersistentList<Instruction> {
                val expressionInstructions = loadExpression(node.expression)

                val newState = node.newState
                if (newState == null) {
                    return expressionInstructions.add(Resume)
                } else {
                    val newStateInstructions = loadExpression(newState)

                    return expressionInstructions
                        .addAll(newStateInstructions)
                        .add(ResumeWithState)
                }
            }

            override fun visit(node: ValNode): PersistentList<Instruction> {
                return loadVal(node)
            }

            override fun visit(node: FunctionDefinitionNode): PersistentList<Instruction> {
                return loadFunctionDefinition(node)
            }

            override fun visit(node: ShapeNode): PersistentList<Instruction> {
                return loadShape(node)
            }

            override fun visit(node: EffectDefinitionNode): PersistentList<Instruction> {
                return loadEffectDefinition(node)
            }
        })
    }

    fun loadModuleStatement(statement: ModuleStatementNode): PersistentList<Instruction> {
        return statement.accept(object : ModuleStatementNode.Visitor<PersistentList<Instruction>> {
            override fun visit(node: EffectDefinitionNode): PersistentList<Instruction> {
                return loadEffectDefinition(node)
            }

            override fun visit(node: TypeAliasNode): PersistentList<Instruction> {
                return persistentListOf()
            }

            override fun visit(node: ShapeNode): PersistentList<Instruction> {
                return loadShape(node)
            }

            override fun visit(node: UnionNode): PersistentList<Instruction> {
                val unionInstructions = persistentListOf(
                    PushValue(IrUnit),
                    LocalStore(node)
                )
                val memberInstructions = node.members.flatMap { member -> loadShape(member) }

                return unionInstructions.addAll(memberInstructions)
            }

            override fun visit(node: FunctionDefinitionNode): PersistentList<Instruction> {
                return loadFunctionDefinition(node)
            }

            override fun visit(node: ValNode): PersistentList<Instruction> {
                return loadVal(node)
            }

            override fun visit(node: VarargsDeclarationNode): PersistentList<Instruction> {
                return loadVarargsDeclaration(node)
            }
        })
    }

    private fun loadEffectDefinition(node: EffectDefinitionNode): PersistentList<Instruction> {
        val effect = (types.variableType(node) as StaticValueType).value as UserDefinedEffect
        return persistentListOf(
            EffectDefine(effect),
            LocalStore(node)
        )
    }

    private fun loadFunctionDefinition(node: FunctionDefinitionNode): PersistentList<Instruction> {
        return persistentListOf(
            loadFunctionValue(node),
            LocalStore(node)
        )
    }

    private fun loadFunctionValue(node: FunctionNode): DefineFunction {
        val bodyInstructions = loadBlock(node.body).add(Return)
        return DefineFunction(
            name = if (node is FunctionDefinitionNode) node.name.value else "anonymous",
            bodyInstructions = bodyInstructions,
            positionalParameters = node.parameters.map { parameter ->
                DefineFunction.Parameter(parameter)
            },
            namedParameters = node.namedParameters.map { parameter ->
                DefineFunction.Parameter(parameter)
            }
        )
    }

    private fun loadShape(node: ShapeBaseNode): PersistentList<Instruction> {
        val tagValue = inspector.shapeTagValue(node)

        return persistentListOf(
            DefineShape(
                tagValue,
                metaType = types.variableType(node) as StaticValueType,
            ),
            LocalStore(node)
        )
    }

    private fun loadVal(node: ValNode): PersistentList<Instruction> {
        val expressionInstructions = loadExpression(node.expression)
        val targetInstructions = loadTarget(node.target)
        return expressionInstructions.addAll(targetInstructions)
    }

    private fun loadTarget(target: TargetNode): PersistentList<Instruction> {
        return when (target) {
            is TargetNode.Ignore ->
                persistentListOf(Discard)

            is TargetNode.Variable ->
                persistentListOf(LocalStore(target))

            is TargetNode.Fields ->
                target.fields.flatMap { (fieldName, fieldTarget) ->
                    persistentListOf(
                        Duplicate,
                        FieldAccess(fieldName = fieldName.identifier, receiverType = types.typeOfTarget(target))
                    ).addAll(loadTarget(fieldTarget))
                }.toPersistentList().add(Discard)

            is TargetNode.Tuple ->
                target.elements.mapIndexed { elementIndex, target ->
                    persistentListOf(
                        Duplicate,
                        TupleAccess(elementIndex = elementIndex)
                    ).addAll(loadTarget(target))
                }.flatten().toPersistentList().add(Discard)
        }
    }

    private fun loadVarargsDeclaration(node: VarargsDeclarationNode): PersistentList<Instruction> {
        val consInstructions = loadExpression(node.cons)
        val nilInstructions = loadExpression(node.nil)

        return consInstructions.addAll(nilInstructions).add(TupleCreate(2)).add(LocalStore(node))
    }

    private fun varargsAccessCons(): Instruction {
        return TupleAccess(0)
    }

    private fun varargsAccessNil(): Instruction {
        return TupleAccess(1)
    }

    private fun typeConditionInstructions(discriminator: Discriminator): PersistentList<Instruction> {
        return persistentListOf(
            TagValueAccess,
            PushValue(IrTagValue(discriminator.tagValue)),
            TagValueEquals
        )
    }

    private fun createLabel(): Label {
        return Label(nextLabel++)
    }

    private fun resolveReference(reference: ReferenceNode): VariableBindingNode {
        return references[reference]
    }
}

private var nextLabel = 1
