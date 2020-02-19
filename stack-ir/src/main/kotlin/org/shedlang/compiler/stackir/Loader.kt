package org.shedlang.compiler.stackir

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import org.shedlang.compiler.*
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.backends.CodeInspector
import org.shedlang.compiler.backends.ModuleCodeInspector
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
        module.name to loadModule(module)
    })
}

private fun loadModule(module: Module.Shed): PersistentList<Instruction> {
    val loader = Loader(
        inspector = ModuleCodeInspector(module),
        references = module.references,
        types = module.types
    )
    return loader.loadModule(module)
}

class Loader(
    private val references: ResolvedReferences,
    private val types: Types,
    private val inspector: CodeInspector
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
            .add(Exit)
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

            override fun visit(node: SymbolNode): PersistentList<Instruction> {
                throw UnsupportedOperationException("not implemented")
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
                                .add(JumpIfTrue(rightLabel.value))
                                .add(PushValue(IrBool(false)))
                                .add(Jump(endLabel.value))
                                .add(rightLabel)
                                .addAll(rightInstructions)
                                .add(Jump(endLabel.value))
                                .add(endLabel)
                        }
                        else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                    }
                    BinaryOperator.SUBTRACT -> IntSubtract
                    BinaryOperator.MULTIPLY -> IntMultiply
                    BinaryOperator.EQUALS -> when (types.typeOfExpression(node.left)) {
                        BoolType -> BoolEquals
                        UnicodeScalarType -> UnicodeScalarEquals
                        IntType -> IntEquals
                        StringType -> StringEquals
                        else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                    }
                    BinaryOperator.GREATER_THAN -> when (types.typeOfExpression(node.left)) {
                        UnicodeScalarType -> UnicodeScalarGreaterThan
                        else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                    }
                    BinaryOperator.GREATER_THAN_OR_EQUAL -> when (types.typeOfExpression(node.left)) {
                        UnicodeScalarType -> UnicodeScalarGreaterThanOrEqual
                        else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                    }
                    BinaryOperator.LESS_THAN -> when (types.typeOfExpression(node.left)) {
                        UnicodeScalarType -> UnicodeScalarLessThan
                        else -> throw UnsupportedOperationException("operator not implemented: " + node.operator)
                    }
                    BinaryOperator.LESS_THAN_OR_EQUAL -> when (types.typeOfExpression(node.left)) {
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
                                .add(JumpIfFalse(rightLabel.value))
                                .add(PushValue(IrBool(true)))
                                .add(Jump(endLabel.value))
                                .add(rightLabel)
                                .addAll(rightInstructions)
                                .add(Jump(endLabel.value))
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
                if (inspector.isCast(node)) {
                    val optionsModuleName = listOf(Identifier("Core"), Identifier("Options"))
                    val loadOptionsModuleInstructions = persistentListOf(
                        ModuleInit(optionsModuleName),
                        ModuleLoad(optionsModuleName)
                    )
                    val parameter = DeclareFunction.Parameter(Identifier("value"), freshNodeId())

                    val failureInstructions = loadOptionsModuleInstructions
                        .add(FieldAccess(Identifier("none"), receiverType = null))
                        .add(Return)
                    val successInstructions = loadOptionsModuleInstructions
                        .add(FieldAccess(Identifier("some"), receiverType = null))
                        .add(LocalLoad(parameter))
                        .add(Call(positionalArgumentCount = 1, namedArgumentNames = listOf()))
                        .add(Return)

                    val discriminator = inspector.discriminatorForCast(node)
                    val failureLabel = createLabel()
                    val bodyInstructions = persistentListOf<Instruction>()
                        .add(LocalLoad(parameter))
                        .addAll(typeConditionInstructions(discriminator))
                        .add(JumpIfFalse(failureLabel.value))
                        .addAll(successInstructions)
                        .add(failureLabel)
                        .addAll(failureInstructions)

                    return persistentListOf(
                        DeclareFunction(
                            name = "cast",
                            positionalParameters = listOf(parameter),
                            bodyInstructions = bodyInstructions,
                            namedParameters = listOf()
                        )
                    )
                } else if (types.typeOfExpression(node.receiver) is VarargsType) {
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
                    val argumentInstructions = loadArguments(node)
                    val call = Call(
                        positionalArgumentCount = node.positionalArguments.size,
                        namedArgumentNames = node.namedArguments.map { argument -> argument.name }
                    )
                    return receiverInstructions.addAll(argumentInstructions).add(call)
                }
            }

            override fun visit(node: PartialCallNode): PersistentList<Instruction> {
                val receiverInstructions = loadExpression(node.receiver)
                val argumentInstructions = loadArguments(node)
                val partialCall = CallPartial(
                    positionalArgumentCount = node.positionalArguments.size,
                    namedArgumentNames = node.namedArguments.map { argument -> argument.name }
                )
                return receiverInstructions.addAll(argumentInstructions).add(partialCall)
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
                    conditionalBodies = node.conditionalBranches.map { branch -> branch.body },
                    elseBranch = node.elseBranch
                )
            }

            override fun visit(node: WhenNode): PersistentList<Instruction> {
                val expressionInstructions = loadExpression(node.expression)

                val conditionInstructions = node.branches.map { branch ->
                    val discriminator = inspector.discriminatorForWhenBranch(node, branch)

                    persistentListOf<Instruction>(Duplicate).addAll(typeConditionInstructions(discriminator))
                }

                return expressionInstructions.addAll(generateBranches(
                    conditionInstructions = conditionInstructions,
                    conditionalBodies = node.branches.map { branch -> branch.body },
                    elseBranch = node.elseBranch
                ))
            }

            private fun generateBranches(
                conditionInstructions: List<PersistentList<Instruction>>,
                conditionalBodies: List<Block>,
                elseBranch: Block?
            ): PersistentList<Instruction> {
                val instructions = mutableListOf<Instruction>()

                val branchBodies = conditionalBodies + elseBranch.nullableToList()
                val bodyInstructions = branchBodies.map { body -> loadBlock(body) }
                val conditionLabels = branchBodies.map { createLabel() }
                val endLabel = createLabel()
                instructions.add(Jump(conditionLabels[0].value))

                conditionInstructions.forEachIndexed { branchIndex, _ ->
                    instructions.add(conditionLabels[branchIndex])
                    val nextConditionLabel = conditionLabels.getOrNull(branchIndex + 1)
                    if (nextConditionLabel != null) {
                        instructions.addAll(conditionInstructions[branchIndex])
                        instructions.add(JumpIfFalse(nextConditionLabel.value))
                    }
                    instructions.addAll(bodyInstructions[branchIndex])
                    instructions.add(Jump(endLabel.value))
                }

                if (elseBranch != null) {
                    instructions.add(conditionLabels.last())
                    instructions.addAll(loadBlock(elseBranch))
                    instructions.add(Jump(endLabel.value))
                }
                instructions.add(endLabel)
                return instructions.toPersistentList()
            }
        })
    }

    private fun loadArguments(node: CallBaseNode): List<Instruction> {
        return node.positionalArguments.flatMap { argument ->
            loadExpression(argument)
        } + node.namedArguments.flatMap { argument ->
            loadExpression(argument.expression)
        }
    }

    fun loadBlock(block: Block): PersistentList<Instruction> {
        val statementInstructions = block.statements.flatMap { statement ->
            loadFunctionStatement(statement)
        }.toPersistentList()

        return if (blockHasReturnValue(block)) {
            statementInstructions
        } else {
            statementInstructions.add(PushValue(IrUnit))
        }
    }

    private fun blockHasReturnValue(block: Block): Boolean {
        val last = block.statements.lastOrNull()
        return last != null && last is ExpressionStatementNode && last.isReturn
    }

    fun loadFunctionStatement(statement: FunctionStatementNode): PersistentList<Instruction> {
        return statement.accept(object : FunctionStatementNode.Visitor<PersistentList<Instruction>> {
            override fun visit(node: ExpressionStatementNode): PersistentList<Instruction> {
                val expressionInstructions = loadExpression(node.expression)
                if (node.type == ExpressionStatementNode.Type.RETURN || node.type == ExpressionStatementNode.Type.TAILREC_RETURN) {
                    return expressionInstructions
                } else if (node.type == ExpressionStatementNode.Type.NO_RETURN) {
                    return expressionInstructions.add(Discard)
                } else {
                    throw UnsupportedOperationException("not implemented")
                }
            }

            override fun visit(node: ValNode): PersistentList<Instruction> {
                return loadVal(node)
            }

            override fun visit(node: FunctionDeclarationNode): PersistentList<Instruction> {
                return loadFunctionDeclaration(node)
            }
        })
    }

    fun loadModuleStatement(statement: ModuleStatementNode): PersistentList<Instruction> {
        return statement.accept(object : ModuleStatementNode.Visitor<PersistentList<Instruction>> {
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

            override fun visit(node: FunctionDeclarationNode): PersistentList<Instruction> {
                return loadFunctionDeclaration(node)
            }

            override fun visit(node: ValNode): PersistentList<Instruction> {
                return loadVal(node)
            }

            override fun visit(node: VarargsDeclarationNode): PersistentList<Instruction> {
                return loadVarargsDeclaration(node)
            }
        })
    }

    private fun loadFunctionDeclaration(node: FunctionDeclarationNode): PersistentList<Instruction> {
        return persistentListOf(
            loadFunctionValue(node),
            LocalStore(node)
        )
    }

    private fun loadFunctionValue(node: FunctionNode): DeclareFunction {
        val bodyInstructions = loadBlock(node.body).add(Return)
        return DeclareFunction(
            name = if (node is FunctionDeclarationNode) node.name.value else "anonymous",
            bodyInstructions = bodyInstructions,
            positionalParameters = node.parameters.map { parameter ->
                DeclareFunction.Parameter(parameter)
            },
            namedParameters = node.namedParameters.map { parameter ->
                DeclareFunction.Parameter(parameter)
            }
        )
    }

    private fun loadShape(node: ShapeBaseNode): PersistentList<Instruction> {
        val shapeFields = inspector.shapeFields(node)
        val tagValue = inspector.shapeTagValue(node)

        return persistentListOf(
            DeclareShape(tagValue, shapeFields),
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
