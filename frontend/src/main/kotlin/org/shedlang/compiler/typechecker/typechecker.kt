package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*

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

data class FunctionType(
    val positionalArguments: List<Type>,
    val namedArguments: Map<String, Type>,
    val returns: Type,
    val effects: List<Effect>
): Type

data class ShapeType(
    val name: String,
    val fields: Map<String, Type>
): Type

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
    variables: MutableMap<Int, Type> = mutableMapOf(),
    resolvedReferences: ResolvedReferences
): TypeContext {
    return TypeContext(
        returnType = null,
        effects = listOf(),
        variables = variables,
        resolvedReferences = resolvedReferences,
        deferred = mutableListOf()
    )
}

class TypeContext(
    val returnType: Type?,
    val effects: List<Effect>,
    private val variables: MutableMap<Int, Type>,
    private val resolvedReferences: ResolvedReferences,
    private val deferred: MutableList<() -> Unit>
) {
    fun typeOf(node: VariableBindingNode): Type {
        val type = variables[node.nodeId]
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
        val type = variables[targetNodeId]
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
        variables += types
    }

    fun enterFunction(returnType: Type, effects: List<Effect>): TypeContext {
        return TypeContext(
            returnType = returnType,
            effects = effects,
            variables = variables,
            resolvedReferences = resolvedReferences,
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

internal fun typeCheck(module: ModuleNode, context: TypeContext) {
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

internal fun typeCheck(statement: ModuleStatementNode, context: TypeContext) {
    return statement.accept(object : ModuleStatementNode.Visitor<Unit> {
        override fun visit(node: ShapeNode) = typeCheck(node, context)
        override fun visit(node: FunctionNode) = typeCheck(node, context)
    })
}

private fun typeCheck(node: ShapeNode, context: TypeContext) {
    for ((fieldName, fields) in node.fields.groupBy({ field -> field.name })) {
        if (fields.size > 1) {
            throw FieldAlreadyDeclaredError(fieldName = fieldName, source = fields[1].source)
        }
    }

    val shapeType = ShapeType(
        name = node.name,
        fields = node.fields.associate({ field -> field.name to evalType(field.type, context) })
    )
    context.addTypes(mapOf(node.nodeId to MetaType(shapeType)))
}

private fun typeCheck(function: FunctionNode, context: TypeContext) {
    val argumentTypes = function.arguments.map(
        { argument -> evalType(argument.type, context) }
    )
    val effects = function.effects.map({ effect -> evalEffect(effect, context) })
    val returnType = evalType(function.returnType, context)

    context.addTypes(mapOf(function.nodeId to FunctionType(
        positionalArguments = argumentTypes,
        namedArguments = mapOf(),
        effects = effects,
        returns = returnType
    )))

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
            typeCheck(node.trueBranch, context)
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
            context.addTypes(mapOf(node.nodeId to type))
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
        override fun visit(node: BooleanLiteralNode): Type {
            return BoolType
        }

        override fun visit(node: IntegerLiteralNode): Type {
            return IntType
        }

        override fun visit(node: StringLiteralNode): Type {
            return StringType
        }

        override fun visit(node: VariableReferenceNode): Type {
            return context.typeOf(node)
        }

        override fun visit(node: BinaryOperationNode): Type {
            verifyType(node.left, context, expected = IntType)
            verifyType(node.right, context, expected = IntType)
            return when (node.operator) {
                Operator.EQUALS -> BoolType
                Operator.ADD, Operator.SUBTRACT, Operator.MULTIPLY -> IntType
            }
        }

        override fun visit(node: CallNode): Type {
            val receiverType = inferType(node.receiver, context)

            for ((name, arguments) in node.namedArguments.groupBy(CallNamedArgumentNode::name)) {
                if (arguments.size > 1) {
                    throw ArgumentAlreadyPassedError(name, source = arguments[1].source)
                }
            }

            if (receiverType is FunctionType) {
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
            } else if (receiverType is MetaType && receiverType.type is ShapeType) {
                val shapeType = receiverType.type

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
            } else {
                val argumentTypes = node.positionalArguments.map { argument -> inferType(argument, context) }
                throw UnexpectedTypeError(
                    expected = FunctionType(argumentTypes, mapOf(), AnyType, effects = listOf()),
                    actual = receiverType,
                    source = node.receiver.source
                )
            }
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
    if (actual != expected) {
        throw UnexpectedTypeError(expected = expected, actual = actual, source = source)
    }
}
