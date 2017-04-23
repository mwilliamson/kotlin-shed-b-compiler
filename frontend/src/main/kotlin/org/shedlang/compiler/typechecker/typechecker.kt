package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*
import java.util.*

interface Effect
object IoEffect : Effect

interface Type

object UnitType: Type
object BoolType : Type
object IntType : Type
object StringType : Type
object AnyType : Type
class MetaType(val type: Type): Type
class EffectType(val effect: Effect): Type

data class ModuleType(
    val fields: Map<String, Type>
): Type

data class FunctionType(
    val positionalArguments: List<Type>,
    val namedArguments: Map<String, Type>,
    val returns: Type,
    val effects: List<Effect>
): Type

interface ShapeType: Type {
    val name: String;
    val fields: Map<String, Type>;
}

data class LazyShapeType(
    override val name: String,
    val getFields: Lazy<Map<String, Type>>
): ShapeType {
    override val fields: Map<String, Type> by getFields
}

interface UnionType: Type {
    val name: String;
    val members: List<Type>;
}

data class LazyUnionType(
    override val name: String,
    private val getMembers: Lazy<List<Type>>
): UnionType {
    override val members: List<Type> by getMembers
}

fun functionType(
    positionalArguments: List<Type> = listOf(),
    namedArguments: Map<String, Type> = mapOf(),
    returns: Type,
    effects: List<Effect> = listOf()
) = FunctionType(
    positionalArguments = positionalArguments,
    namedArguments = namedArguments,
    returns = returns,
    effects = effects
)

fun positionalFunctionType(arguments: List<Type>, returns: Type)
    = FunctionType(
        positionalArguments = arguments,
        namedArguments = mapOf(),
        returns = returns,
        effects = listOf()
    )

fun newTypeContext(
    nodeTypes: MutableMap<Int, Type> = mutableMapOf(),
    resolvedReferences: ResolvedReferences,
    getModule: (String) -> ModuleType
): TypeContext {
    return TypeContext(
        returnType = null,
        effects = listOf(),
        nodeTypes = nodeTypes,
        resolvedReferences = resolvedReferences,
        getModule = getModule,
        deferred = mutableListOf()
    )
}

class TypeContext(
    val returnType: Type?,
    val effects: List<Effect>,
    private val nodeTypes: MutableMap<Int, Type>,
    private val resolvedReferences: ResolvedReferences,
    private val getModule: (String) -> ModuleType,
    private val deferred: MutableList<() -> Unit>
) {

    fun moduleType(module: String): ModuleType {
        return getModule(module)
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

    fun enterFunction(returnType: Type, effects: List<Effect>): TypeContext {
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
    : TypeCheckError("Expected type $expected but was $actual", source)
class WrongNumberOfArgumentsError(val expected: Int, val actual: Int, source: Source)
    : TypeCheckError("Expected $expected arguments, but got $actual", source)
class MissingArgumentError(val argumentName: String, source: Source)
    : TypeCheckError("Call is missing argument: $argumentName", source)
class ExtraArgumentError(val argumentName: String, source: Source)
    : TypeCheckError("Call has extra argument: $argumentName", source)
class ArgumentAlreadyPassedError(val argumentName: String, source: Source)
    : TypeCheckError("Argument has already been passed: $argumentName", source)
class PositionalArgumentPassedToShapeConstructorError(source: Source)
    : TypeCheckError("Positional arguments cannot be passed to shape constructors", source)
class ReturnOutsideOfFunctionError(source: Source)
    : TypeCheckError("Cannot return outside of a function", source)
class NoSuchFieldError(val fieldName: String, source: Source)
    : TypeCheckError("No such field: " + fieldName, source)
class FieldAlreadyDeclaredError(val fieldName: String, source: Source)
    : TypeCheckError("Field has already been declared: ${fieldName}", source)
class UnhandledEffectError(val effect: Effect, source: Source)
    : TypeCheckError("Unhandled effect: ${effect}", source)

internal interface NodeTypes {
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

internal fun typeCheck(
    module: ModuleNode,
    nodeTypes: Map<Int, Type>,
    resolvedReferences: ResolvedReferences,
    getModule: (String) -> ModuleType
): NodeTypes {
    val mutableNodeTypes = HashMap(nodeTypes)
    val typeContext = newTypeContext(
        nodeTypes = mutableNodeTypes,
        resolvedReferences = resolvedReferences,
        getModule = getModule
    )
    typeCheck(module, typeContext)
    return NodeTypesMap(mutableNodeTypes)
}

internal fun typeCheck(module: ModuleNode, context: TypeContext) {
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
}

internal fun typeCheck(import: ImportNode, context: TypeContext) {
    context.addType(import, context.moduleType(import.module))
}

internal fun typeCheck(statement: ModuleStatementNode, context: TypeContext) {
    return statement.accept(object : ModuleStatementNode.Visitor<Unit> {
        override fun visit(node: ShapeNode) = typeCheck(node, context)
        override fun visit(node: UnionNode) = typeCheck(node, context)
        override fun visit(node: FunctionNode) = typeCheck(node, context)
    })
}

private fun typeCheck(node: ShapeNode, context: TypeContext) {
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
        getFields = fields
    )
    context.addType(node, MetaType(shapeType))
    context.defer({
        fields.value
    })
}

private fun typeCheck(node: UnionNode, context: TypeContext) {
    // TODO: check for duplicates in members
    // TODO: check for circularity
    // TODO: test laziness

    val members = lazy({ node.members.map({ member -> evalType(member, context) }) })
    val unionType = LazyUnionType(
        name = node.name,
        getMembers = members
    )

    context.addType(node, MetaType(unionType))
    context.defer({
        members.value
    })
}

private fun typeCheck(function: FunctionNode, context: TypeContext) {
    val argumentTypes = function.arguments.map(
        { argument -> evalType(argument.type, context) }
    )
    val effects = function.effects.map({ effect -> evalEffect(effect, context) })
    val returnType = evalType(function.returnType, context)

    val type = FunctionType(
        positionalArguments = argumentTypes,
        namedArguments = mapOf(),
        effects = effects,
        returns = returnType
    )
    context.addType(function, type)

    context.defer({
        context.addTypes(function.arguments.zip(
            argumentTypes,
            { argument, argumentType -> argument.nodeId to argumentType }
        ).toMap())
        val bodyContext = context.enterFunction(
            returnType = returnType,
            effects = effects
        )
        typeCheck(function.body, bodyContext)
    })
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
            val type = inferType(node.expression, context)
            context.addType(node, type)
        }
    })
}

private fun typeCheck(statements: List<StatementNode>, context: TypeContext) {
    for (statement in statements) {
        typeCheck(statement, context)
    }
}

private fun typeCheck(expression: ExpressionNode, context: TypeContext): Unit {
    inferType(expression, context)
}

internal fun inferType(expression: ExpressionNode, context: TypeContext) : Type {
    return expression.accept(object : ExpressionNode.Visitor<Type> {
        override fun visit(node: UnitLiteralNode) = UnitType
        override fun visit(node: BooleanLiteralNode) = BoolType
        override fun visit(node: IntegerLiteralNode) = IntType
        override fun visit(node: StringLiteralNode) = StringType
        override fun visit(node: VariableReferenceNode) = context.typeOf(node)

        override fun visit(node: BinaryOperationNode): Type {
            verifyType(node.left, context, expected = IntType)
            verifyType(node.right, context, expected = IntType)
            return when (node.operator) {
                Operator.EQUALS -> BoolType
                Operator.ADD, Operator.SUBTRACT, Operator.MULTIPLY -> IntType
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
                return inferConstructorCallType(node, shapeType)
            } else {
                val argumentTypes = node.positionalArguments.map { argument -> inferType(argument, context) }
                throw UnexpectedTypeError(
                    expected = FunctionType(argumentTypes, mapOf(), AnyType, effects = listOf()),
                    actual = receiverType,
                    source = node.receiver.source
                )
            }
        }

        private fun inferFunctionCallType(node: CallNode, receiverType: FunctionType): Type {
            node.positionalArguments.zip(receiverType.positionalArguments, { arg, argType -> verifyType(arg, context, expected = argType) })
            if (receiverType.positionalArguments.size != node.positionalArguments.size) {
                throw WrongNumberOfArgumentsError(
                    expected = receiverType.positionalArguments.size,
                    actual = node.positionalArguments.size,
                    source = node.source
                )
            }

            val unhandledEffects = receiverType.effects - context.effects
            if (unhandledEffects.isNotEmpty()) {
                throw UnhandledEffectError(unhandledEffects[0], source = node.source)
            }

            return receiverType.returns
        }

        private fun inferConstructorCallType(node: CallNode, shapeType: ShapeType): Type {
            if (node.positionalArguments.any()) {
                throw PositionalArgumentPassedToShapeConstructorError(source = node.positionalArguments.first().source)
            }

            for (argument in node.namedArguments) {
                val fieldType = shapeType.fields[argument.name]
                if (fieldType == null) {
                    throw ExtraArgumentError(argument.name, source = argument.source)
                } else {
                    verifyType(argument.expression, context, expected = fieldType)
                }
            }

            val missingFieldNames = shapeType.fields.keys - node.namedArguments.map({ argument -> argument.name })

            for (fieldName in missingFieldNames) {
                throw MissingArgumentError(fieldName, source = node.source)
            }

            return shapeType
        }

        override fun visit(node: FieldAccessNode): Type {
            val receiverType = inferType(node.receiver, context)
            if (receiverType is ShapeType) {
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
    if (from == to) {
        return true
    }

    if (from is UnionType) {
        return from.members.all({ member -> canCoerce(from = member, to = to) })
    }

    if (to is UnionType) {
        return to.members.any({ member -> canCoerce(from = from, to = member) })
    }

    return false
}
