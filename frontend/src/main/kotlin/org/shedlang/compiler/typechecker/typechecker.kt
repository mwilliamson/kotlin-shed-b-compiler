package org.shedlang.compiler.typechecker

import org.shedlang.compiler.all
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.*
import org.shedlang.compiler.zip3
import java.util.*

fun newTypeContext(
    nodeTypes: MutableMap<Int, Type> = mutableMapOf(),
    resolvedReferences: ResolvedReferences,
    getModule: (ImportPath) -> ModuleType
): TypeContext {
    return TypeContext(
        returnType = null,
        effects = setOf(),
        nodeTypes = nodeTypes,
        resolvedReferences = resolvedReferences,
        getModule = getModule,
        deferred = mutableListOf()
    )
}

class TypeContext(
    val returnType: Type?,
    val effects: Set<Effect>,
    private val nodeTypes: MutableMap<Int, Type>,
    private val resolvedReferences: ResolvedReferences,
    private val getModule: (ImportPath) -> ModuleType,
    private val deferred: MutableList<() -> Unit>
) {

    fun moduleType(path: ImportPath): ModuleType {
        return getModule(path)
    }

    fun typeOf(node: VariableBindingNode): Type {
        val type = nodeTypes[node.nodeId]
        if (type == null) {
            // TODO: test this
            throw CompilerError(
                "type of ${node.name} is unknown",
                source = node.source
            )
        } else {
            return type
        }
    }

    fun typeOf(reference: ReferenceNode): Type {
        val targetNodeId = resolvedReferences[reference]
        val type = nodeTypes[targetNodeId]
        if (type == null) {
            throw CompilerError(
                "type of ${reference.name} is unknown",
                source = reference.source
            )
        } else {
            return type
        }
    }

    fun addTypes(types: Map<Int, Type>) {
        nodeTypes += types
    }

    fun addType(node: VariableBindingNode, type: Type) {
        nodeTypes[node.nodeId] = type
    }

    fun addType(node: ReferenceNode, type: Type) {
        val targetNodeId = resolvedReferences[node]
        nodeTypes[targetNodeId] = type
    }

    fun enterFunction(returnType: Type, effects: Set<Effect>): TypeContext {
        return TypeContext(
            returnType = returnType,
            effects = effects,
            nodeTypes = nodeTypes,
            resolvedReferences = resolvedReferences,
            getModule = getModule,
            deferred = deferred
        ).enterScope()
    }

    fun enterScope(): TypeContext {
        return TypeContext(
            returnType = returnType,
            effects = effects,
            nodeTypes = HashMap(nodeTypes),
            resolvedReferences = resolvedReferences,
            getModule = getModule,
            deferred = deferred
        )
    }

    fun defer(deferred: () -> Unit) {
        this.deferred.add(deferred)
    }

    fun undefer() {
        while (this.deferred.isNotEmpty()) {
            val index = this.deferred.size - 1
            val deferred = this.deferred[index]
            this.deferred.removeAt(index)
            deferred()
        }
    }
}

/**
 * This indicates a bug in the compiler or its calling code
 */
open class CompilerError(message: String, val source: Source) : Exception(message)
class UnknownTypeError(val name: String, source: Source)
    : CompilerError("type of ${name} is unknown", source = source)
class NotFunctionTypeError(val actual: Type, source: Source)
    : TypeCheckError("expected function type but was ${actual}", source)

open class TypeCheckError(message: String?, val source: Source) : Exception(message)
internal class BadStatementError(source: Source)
    : TypeCheckError("Bad statement", source)
class UnresolvedReferenceError(val name: String, source: Source)
    : TypeCheckError("Unresolved reference: " + name, source)
class UninitialisedVariableError(val name: String, source: Source)
    : TypeCheckError("Uninitialised variable: " + name, source)
class RedeclarationError(val name: String, source: Source)
    : TypeCheckError("Variable with name ${name} has already been declared", source)
class UnexpectedTypeError(val expected: Type, val actual: Type, source: Source)
    : TypeCheckError("Expected type ${expected.shortDescription} but was ${actual.shortDescription}", source)
class WrongNumberOfArgumentsError(val expected: Int, val actual: Int, source: Source)
    : TypeCheckError("Expected $expected arguments, but got $actual", source)
class WrongNumberOfTypeArgumentsError(val expected: Int, val actual: Int, source: Source)
    : TypeCheckError("Expected $expected type arguments, but got $actual", source)
class MissingArgumentError(val argumentName: String, source: Source)
    : TypeCheckError("Call is missing argument: $argumentName", source)
class ExtraArgumentError(val argumentName: String, source: Source)
    : TypeCheckError("Call has extra argument: $argumentName", source)
class ArgumentAlreadyPassedError(val argumentName: String, source: Source)
    : TypeCheckError("Argument has already been passed: $argumentName", source)
class PositionalArgumentPassedToShapeConstructorError(source: Source)
    : TypeCheckError("Positional arguments cannot be passed to shape constructors", source)
class CouldNotInferTypeParameterError(parameter: TypeParameter, source: Source)
    : TypeCheckError("Could not infer type for type parameter $parameter", source)
class ReturnOutsideOfFunctionError(source: Source)
    : TypeCheckError("Cannot return outside of a function", source)
class NoSuchFieldError(val fieldName: String, source: Source)
    : TypeCheckError("No such field: " + fieldName, source)
class FieldAlreadyDeclaredError(val fieldName: String, source: Source)
    : TypeCheckError("Field has already been declared: ${fieldName}", source)
class UnhandledEffectError(val effect: Effect, source: Source)
    : TypeCheckError("Unhandled effect: ${effect}", source)
class InvalidOperationError(val operator: Operator, val operands: List<Type>, source: Source)
    : TypeCheckError(
        "Operation ${operator} is not valid for operands ${operands.map({operand -> operand.shortDescription}).joinToString(", ")}",
        source
    )
class MissingReturnTypeError(message: String, source: Source)
    : TypeCheckError(message, source)

interface NodeTypes {
    fun typeOf(node: VariableBindingNode): Type
}

internal class NodeTypesMap(private val nodeTypes: Map<Int, Type>) : NodeTypes {
    override fun typeOf(node: VariableBindingNode): Type {
        val type = nodeTypes[node.nodeId]
        if (type == null) {
            throw UnknownTypeError(name = node.name, source = node.source)
        } else {
            return type
        }
    }
}

data class TypeCheckResult(
    val types: NodeTypes,
    val moduleType: ModuleType
)

internal fun typeCheck(
    module: ModuleNode,
    nodeTypes: Map<Int, Type>,
    resolvedReferences: ResolvedReferences,
    getModule: (ImportPath) -> ModuleType
): TypeCheckResult {
    val mutableNodeTypes = HashMap(nodeTypes)
    val typeContext = newTypeContext(
        nodeTypes = mutableNodeTypes,
        resolvedReferences = resolvedReferences,
        getModule = getModule
    )
    val moduleType = typeCheck(module, typeContext)
    return TypeCheckResult(
        types = NodeTypesMap(mutableNodeTypes),
        moduleType = moduleType
    )
}

internal fun typeCheck(module: ModuleNode, context: TypeContext): ModuleType {
    for (import in module.imports) {
        typeCheck(import, context)
    }

    val (typeDeclarations, otherStatements) = module.body
        .partition({ statement -> statement is TypeDeclarationNode })

    for (typeDeclaration in typeDeclarations) {
        typeCheck(typeDeclaration, context)
    }

    for (statement in otherStatements) {
        typeCheck(statement, context)
    }

    context.undefer()

    return ModuleType(fields = module.body.filterIsInstance<VariableBindingNode>().associateBy(
        { statement -> statement.name },
        { statement -> context.typeOf(statement) }
    ))
}

internal fun typeCheck(import: ImportNode, context: TypeContext) {
    context.addType(import, context.moduleType(import.path))
}

internal fun typeCheck(statement: ModuleStatementNode, context: TypeContext) {
    return statement.accept(object : ModuleStatementNode.Visitor<Unit> {
        override fun visit(node: ShapeNode) = typeCheck(node, context)
        override fun visit(node: UnionNode) = typeCheck(node, context)
        override fun visit(node: FunctionDeclarationNode) = typeCheck(node, context)
        override fun visit(node: ValNode) = typeCheck(node, context)
    })
}

private fun typeCheck(node: ShapeNode, context: TypeContext) {
    val typeParameters = typeCheckTypeParameters(node.typeParameters, context)

    for ((fieldName, fields) in node.fields.groupBy({ field -> field.name })) {
        if (fields.size > 1) {
            throw FieldAlreadyDeclaredError(fieldName = fieldName, source = fields[1].source)
        }
    }

    // TODO: test laziness
    val fields = lazy({
        node.fields.associate({ field -> field.name to evalType(field.type, context) })
    })

    val shapeType = LazyShapeType(
        name = node.name,
        getFields = fields,
        typeParameters = typeParameters,
        typeArguments = typeParameters
    )
    val type = if (node.typeParameters.isEmpty()) {
        shapeType
    } else {
        TypeFunction(typeParameters, shapeType)
    }
    context.addType(node, MetaType(type))
    context.defer({
        fields.value
        checkType(type, source = node.source)
    })
}

private fun typeCheck(node: UnionNode, context: TypeContext) {
    // TODO: check for duplicates in members
    // TODO: check for circularity
    // TODO: test laziness
    val typeParameters = typeCheckTypeParameters(node.typeParameters, context)

    val members = lazy({ node.members.map({ member -> evalType(member, context) }) })
    val unionType = LazyUnionType(
        name = node.name,
        getMembers = members,
        typeArguments = typeParameters
    )
    val type = if (node.typeParameters.isEmpty()) {
        unionType
    } else {
        TypeFunction(typeParameters, unionType)
    }

    context.addType(node, MetaType(type))
    context.defer({
        members.value
    })
}

private fun typeCheckTypeParameters(parameters: List<TypeParameterNode>, context: TypeContext): List<TypeParameter> {
    return parameters.map({ parameter ->
        val typeParameter = TypeParameter(name = parameter.name, variance = parameter.variance)
        context.addType(parameter, MetaType(typeParameter))
        typeParameter
    })
}

private fun typeCheck(function: FunctionDeclarationNode, context: TypeContext) {
    val type = typeCheckFunction(function, context)
    context.addType(function, type)
}

internal fun typeCheckFunction(function: FunctionNode, context: TypeContext): Type {
    val typeParameters = typeCheckTypeParameters(function.typeParameters, context)

    val argumentTypes = function.arguments.map(
        { argument -> evalType(argument.type, context) }
    )
    val effects = function.effects.map({ effect -> evalEffect(effect, context) }).toSet()

    val body = function.body
    val returnType = when (body) {
        is FunctionBody.Expression -> {
            val returnTypeNode = function.returnType
            if (returnTypeNode == null) {
                inferType(body.expression, context)
            } else {
                val returnType = evalType(returnTypeNode, context)
                verifyType(expression = body.expression, expected = returnType, context = context)
                returnType
            }
        }
        is FunctionBody.Statements -> {
            val returnTypeNode = function.returnType
            if (returnTypeNode == null) {
                throw UnsupportedOperationException("TODO")
            } else {
                val returnType = evalType(returnTypeNode, context)

                context.defer({
                    context.addTypes(function.arguments.zip(
                        argumentTypes,
                        { argument, argumentType -> argument.nodeId to argumentType }
                    ).toMap())
                    val bodyContext = context.enterFunction(
                        returnType = returnType,
                        effects = effects
                    )
                    typeCheck(body.nodes, bodyContext)
                })

                returnType
            }
        }
    }

    val functionType = FunctionType(
        typeParameters = typeParameters,
        positionalArguments = argumentTypes,
        namedArguments = mapOf(),
        effects = effects,
        returns = returnType
    )

    checkReturns(function, functionType)

    return functionType
}

private fun typeCheck(type: TypeNode, context: TypeContext) {
    evalType(type, context)
}

internal fun evalType(type: TypeNode, context: TypeContext): Type {
    return type.accept(object : TypeNode.Visitor<Type> {
        override fun visit(node: TypeReferenceNode): Type {
            val metaType = context.typeOf(node)
            return when (metaType) {
                is MetaType -> metaType.type
                else -> throw UnexpectedTypeError(
                    expected = MetaType(AnyType),
                    actual = metaType,
                    source = node.source
                )
            }
        }

        override fun visit(node: TypeApplicationNode): Type {
            val receiver = evalType(node.receiver, context)
            val arguments = node.arguments.map({ argument -> evalType(argument, context) })
            if (receiver is TypeFunction) {
                return applyType(receiver, arguments)
            } else {
                // TODO: throw a more appropriate exception
                throw CompilerError("TODO", source = node.source)
            }
        }

        override fun visit(node: FunctionTypeNode): Type {
            val type = FunctionType(
                typeParameters = listOf(),
                positionalArguments = node.arguments.map({ argument -> evalType(argument, context) }),
                namedArguments = mapOf(),
                returns = evalType(node.returnType, context),
                effects = setOf()
            )
            checkType(type, source = node.source)
            return type
        }
    })
}

internal fun evalEffect(node: VariableReferenceNode, context: TypeContext): Effect {
    val effectType = context.typeOf(node)
    if (effectType is EffectType) {
        return effectType.effect
    } else {
        // TODO: throw a more appropriate exception
        throw CompilerError("TODO", source = node.source)
    }
}

internal fun typeCheck(statement: StatementNode, context: TypeContext) {
    statement.accept(object : StatementNode.Visitor<Unit> {
        override fun visit(node: BadStatementNode) {
            throw BadStatementError(node.source)
        }

        override fun visit(node: IfStatementNode) {
            verifyType(node.condition, context, expected = BoolType)

            val trueContext = context.enterScope()

            if (node.condition is IsNode && node.condition.expression is VariableReferenceNode) {
                trueContext.addType(node.condition.expression, evalType(node.condition.type, context))
            }

            typeCheck(node.trueBranch, trueContext)
            typeCheck(node.falseBranch, context)
        }

        override fun visit(node: ReturnNode): Unit {
            if (context.returnType == null) {
                throw ReturnOutsideOfFunctionError(node.source)
            } else {
                verifyType(node.expression, context, expected = context.returnType)
            }
        }

        override fun visit(node: ExpressionStatementNode) {
            typeCheck(node.expression, context)
        }

        override fun visit(node: ValNode) {
            typeCheck(node, context)
        }
    })
}

private fun typeCheck(node: ValNode, context: TypeContext) {
    val type = inferType(node.expression, context)
    context.addType(node, type)
}

private fun typeCheck(statements: List<StatementNode>, context: TypeContext) {
    for (statement in statements) {
        typeCheck(statement, context)
    }
}

private fun typeCheck(expression: ExpressionNode, context: TypeContext): Unit {
    inferType(expression, context)
}

private data class OperationType(val operator: Operator, val left: Type, val right: Type)

internal fun inferType(expression: ExpressionNode, context: TypeContext) : Type {
    return expression.accept(object : ExpressionNode.Visitor<Type> {
        override fun visit(node: UnitLiteralNode) = UnitType
        override fun visit(node: BooleanLiteralNode) = BoolType
        override fun visit(node: IntegerLiteralNode) = IntType
        override fun visit(node: StringLiteralNode) = StringType
        override fun visit(node: VariableReferenceNode) = context.typeOf(node)

        override fun visit(node: BinaryOperationNode): Type {
            val leftType = inferType(node.left, context)
            val rightType = inferType(node.right, context)

            return when (OperationType(node.operator, leftType, rightType)) {
                OperationType(Operator.EQUALS, IntType, IntType) -> BoolType
                OperationType(Operator.ADD, IntType, IntType) -> IntType
                OperationType(Operator.SUBTRACT, IntType, IntType) -> IntType
                OperationType(Operator.MULTIPLY, IntType, IntType) -> IntType

                OperationType(Operator.EQUALS, StringType, StringType) -> BoolType
                OperationType(Operator.ADD, StringType, StringType) -> StringType

                OperationType(Operator.EQUALS, BoolType, BoolType) -> BoolType

                else -> throw InvalidOperationError(
                    operator = node.operator,
                    operands = listOf(leftType, rightType),
                    source = node.source
                )
            }
        }

        override fun visit(node: IsNode): Type {
            // TODO: test expression and type checking

            typeCheck(node.expression, context)
            evalType(node.type, context)

            return BoolType
        }

        override fun visit(node: CallNode): Type {
            val receiverType = inferType(node.receiver, context)

            for ((name, arguments) in node.namedArguments.groupBy(CallNamedArgumentNode::name)) {
                if (arguments.size > 1) {
                    throw ArgumentAlreadyPassedError(name, source = arguments[1].source)
                }
            }

            if (receiverType is FunctionType) {
                return inferFunctionCallType(node, receiverType)
            } else if (receiverType is MetaType && receiverType.type is ShapeType) {
                val shapeType = receiverType.type
                return inferConstructorCallType(node, null, shapeType)
            } else if (receiverType is MetaType && receiverType.type is TypeFunction && receiverType.type.type is ShapeType) {
                return inferConstructorCallType(node, receiverType.type, receiverType.type.type)
            } else {
                val argumentTypes = node.positionalArguments.map { argument -> inferType(argument, context) }
                throw UnexpectedTypeError(
                    expected = FunctionType(
                        typeParameters = listOf(),
                        positionalArguments = argumentTypes,
                        namedArguments = mapOf(),
                        returns = AnyType,
                        effects = setOf()
                    ),
                    actual = receiverType,
                    source = node.receiver.source
                )
            }
        }

        private fun inferFunctionCallType(node: CallNode, receiverType: FunctionType): Type {
            val typeParameterBindings = checkArguments(
                call = node,
                typeParameters = receiverType.typeParameters,
                positionalParameters = receiverType.positionalArguments,
                namedParameters = receiverType.namedArguments
            )

            val unhandledEffects = receiverType.effects - context.effects
            if (unhandledEffects.isNotEmpty()) {
                throw UnhandledEffectError(unhandledEffects.first(), source = node.source)
            }

            // TODO: handle unconstrained types
            return replaceTypes(receiverType.returns, typeParameterBindings)
        }

        private fun inferConstructorCallType(node: CallNode, typeFunction: TypeFunction?, shapeType: ShapeType): Type {
            if (node.positionalArguments.any()) {
                throw PositionalArgumentPassedToShapeConstructorError(source = node.positionalArguments.first().source)
            }

            val typeParameterBindings = checkArguments(
                call = node,
                typeParameters = typeFunction?.parameters ?: listOf(),
                positionalParameters = listOf(),
                namedParameters = shapeType.fields
            )

            if (typeFunction == null) {
                return shapeType
            } else {
                return applyType(typeFunction, typeFunction.parameters.map({ parameter ->
                    typeParameterBindings[parameter]!!
                }))
            }
        }

        private fun checkArguments(
            call: CallNode,
            typeParameters: List<TypeParameter>,
            positionalParameters: List<Type>,
            namedParameters: Map<String, Type>
        ): Map<TypeParameter, Type> {
            val constraints = if (call.typeArguments.isEmpty()) {
                TypeConstraintSolver(parameters = typeParameters.toMutableSet())
            } else {
                if (call.typeArguments.size != typeParameters.size) {
                    throw WrongNumberOfTypeArgumentsError(
                        expected = typeParameters.size,
                        actual = call.typeArguments.size,
                        source = call.source
                    )
                }

                TypeConstraintSolver(
                    parameters = setOf(),
                    bindings = typeParameters.zip(call.typeArguments, { typeParameter, typeArgument ->
                        typeParameter to evalType(typeArgument, context)
                    }).toMap().toMutableMap(),
                    closed = typeParameters.toMutableSet()
                )
            }

            val positionalArguments = call.positionalArguments.zip(positionalParameters)
            if (positionalParameters.size != call.positionalArguments.size) {
                throw WrongNumberOfArgumentsError(
                    expected = positionalParameters.size,
                    actual = call.positionalArguments.size,
                    source = call.source
                )
            }

            val namedArguments = call.namedArguments.map({ argument ->
                val fieldType = namedParameters[argument.name]
                if (fieldType == null) {
                    throw ExtraArgumentError(argument.name, source = argument.source)
                } else {
                    argument.expression to fieldType
                }
            })

            val missingNamedArguments = namedParameters.keys - call.namedArguments.map({ argument -> argument.name })
            for (missingNamedArgument in missingNamedArguments) {
                throw MissingArgumentError(missingNamedArgument, source = call.source)
            }

            val arguments = positionalArguments + namedArguments

            for (argument in arguments) {
                val actualType = inferType(argument.first, context)
                val formalType = argument.second
                if (!constraints.coerce(from = actualType, to = formalType)) {
                    throw UnexpectedTypeError(
                        expected = formalType,
                        actual = actualType,
                        source = argument.first.source
                    )
                }
            }
            return typeParameters.associate({ parameter ->
                val boundType = constraints.bindings[parameter]
                parameter to if (boundType != null) {
                    boundType
                } else if (parameter.variance == Variance.COVARIANT) {
                    NothingType
                } else if (parameter.variance == Variance.CONTRAVARIANT) {
                    AnyType
                } else {
                    throw CouldNotInferTypeParameterError(
                        parameter = parameter,
                        source = call.source
                    )
                }
            })
        }

        override fun visit(node: FieldAccessNode): Type {
            val receiverType = inferType(node.receiver, context)
            if (receiverType is HasFieldsType) {
                val fieldType = receiverType.fields[node.fieldName]
                if (fieldType == null) {
                    throw NoSuchFieldError(
                        fieldName = node.fieldName,
                        source = node.source
                    )
                } else {
                    return fieldType
                }
            } else {
                throw NoSuchFieldError(
                    fieldName = node.fieldName,
                    source = node.source
                )
            }
        }

        override fun visit(node: FunctionExpressionNode): Type {
            return typeCheckFunction(node, context)
        }
    })
}

private fun verifyType(expression: ExpressionNode, context: TypeContext, expected: Type) {
    val type = inferType(expression, context)
    verifyType(expected = expected, actual = type, source = expression.source)
}

private fun verifyType(expected: Type, actual: Type, source: Source) {
    if (!canCoerce(from = actual, to = expected)) {
        throw UnexpectedTypeError(expected = expected, actual = actual, source = source)
    }
}

internal fun canCoerce(from: Type, to: Type): Boolean {
    return coerce(from = from, to = to) is CoercionResult.Success
}

internal fun isEquivalentType(first: Type, second: Type): Boolean {
    return canCoerce(from = first, to = second) && canCoerce(from = second, to = first)
}

internal fun coerce(
    from: Type,
    to: Type,
    parameters: Set<TypeParameter> = setOf()
): CoercionResult {
    return coerce(listOf(from to to), parameters = parameters)
}

internal fun coerce(
    constraints: List<Pair<Type, Type>>,
    parameters: Set<TypeParameter> = setOf()
): CoercionResult {
    val solver = TypeConstraintSolver(parameters = parameters)
    for ((from, to) in constraints) {
        if (!solver.coerce(from = from, to = to)) {
            return CoercionResult.Failure
        }
    }
    return CoercionResult.Success(solver.bindings)
}

internal sealed class CoercionResult {
    internal class Success(val bindings: Map<TypeParameter, Type>): CoercionResult()
    internal object Failure: CoercionResult()
}

private class TypeConstraintSolver(
    private val parameters: Set<TypeParameter>,
    internal val bindings: MutableMap<TypeParameter, Type> = mutableMapOf(),
    private val closed: MutableSet<TypeParameter> = mutableSetOf()
) {
    fun coerce(from: Type, to: Type): Boolean {
        if (from == to || to == AnyType || from == NothingType) {
            return true
        }

        // TODO: deal with type parameters
        if (from is UnionType) {
            return from.members.all({ member -> coerce(from = member, to = to) })
        }

        if (to is UnionType) {
            return to.members.any({ member -> coerce(from = from, to = member) })
        }

        if (from is FunctionType && to is FunctionType) {
            return (
                from.typeParameters.isEmpty() && to.typeParameters.isEmpty() &&
                from.positionalArguments.size == to.positionalArguments.size &&
                from.positionalArguments.zip(to.positionalArguments, { fromArg, toArg -> coerce(from = toArg, to = fromArg) }).all() &&
                from.namedArguments.keys == to.namedArguments.keys &&
                from.namedArguments.all({ fromArg -> coerce(from = to.namedArguments[fromArg.key]!!, to = fromArg.value) }) &&
                from.effects == to.effects &&
                coerce(from = from.returns, to = to.returns)
            )
        }

        if (from is ShapeType && to is ShapeType) {
            return from.shapeId == to.shapeId && zip3(
                from.typeParameters,
                from.typeArguments,
                to.typeArguments,
                { parameter, fromArg, toArg -> when (parameter.variance) {
                    Variance.INVARIANT -> isEquivalentType(fromArg, toArg)
                    Variance.COVARIANT -> coerce(from = fromArg, to = toArg)
                    Variance.CONTRAVARIANT -> coerce(from = toArg, to = fromArg)
                }}
            ).all()
        }

        if (to is TypeParameter && to in parameters) {
            val boundType = bindings[to]
            if (boundType == null) {
                bindings[to] = from
                return true
            } else if (to in closed) {
                return false
            } else {
                bindings[to] = union(boundType, from)
                return true
            }
        }

        if (from is TypeParameter && from in parameters) {
            val boundType = bindings[from]
            if (boundType == null) {
                bindings[from] = to
                closed.add(from)
                return true
            } else if (boundType == to) {
                return true
            } else {
                return false
            }
        }

        return false
    }

    private fun isEquivalentType(left: Type, right: Type): Boolean {
        return coerce(from = left, to = right) && coerce(from = right, to = left)
    }
}

private fun checkType(type: Type, source: Source) {
    val result = validateType(type)
    if (result.errors.isNotEmpty()) {
        // TODO: add more appropriate subclass
        throw TypeCheckError(result.errors.first(), source = source)
    }
}
