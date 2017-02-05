package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.*

interface Type

object UnitType: Type
object BoolType : Type
object IntType : Type
object StringType : Type
object AnyType : Type
class MetaType(val type: Type): Type

data class FunctionType(val arguments: List<Type>, val returns: Type): Type

class TypeContext(
    val returnType: Type?,
    private val variables: MutableMap<Int, Type>,
    private val variableReferences: VariableReferences
) {
    fun typeOf(reference: ReferenceNode): Type {
        val targetNodeId = variableReferences[reference]
        if (targetNodeId == null) {
            throw CompilerError(
                "reference ${reference.name} is unresolved",
                source = reference.source
            )
        } else {
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
    }

    fun addTypes(types: Map<Int, Type>) {
        variables += types
    }

    fun enterFunction(returnType: Type): TypeContext {
        return TypeContext(
            returnType = returnType,
            variables = variables,
            variableReferences = variableReferences
        )
    }
}

/**
 * This indicates a bug in the compiler or its calling code
 */
open class CompilerError(message: String, val source: Source) : Exception(message)
class UnknownTypeError(val name: String, source: Source)
    : CompilerError("type of ${name} is unknown", source = source)

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
class ReturnOutsideOfFunctionError(source: Source)
    : TypeCheckError("Cannot return outside of a function", source)

fun typeCheck(module: ModuleNode, context: TypeContext) {
    val functionTypes = module.body.associateBy(
        FunctionNode::nodeId,
        { function -> inferType(function, context) }
    )

    context.addTypes(functionTypes)

    for (statement in module.body) {
        typeCheck(statement, context)
    }
}

fun inferType(function: FunctionNode, context: TypeContext): FunctionType {
    val argumentTypes = function.arguments.map(
        { argument -> evalType(argument.type, context) }
    )
    val returnType = evalType(function.returnType, context)
    return FunctionType(argumentTypes, returnType)
}

fun typeCheck(function: FunctionNode, context: TypeContext) {
    val argumentTypes = function.arguments.associateBy(
        ArgumentNode::nodeId,
        { argument -> evalType(argument.type, context) }
    )
    val returnType = evalType(function.returnType, context)
    context.addTypes(argumentTypes)
    val bodyContext = context.enterFunction(returnType = returnType)
    typeCheck(function.body, bodyContext)
}

fun evalType(type: TypeNode, context: TypeContext): Type {
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

fun typeCheck(statement: StatementNode, context: TypeContext) {
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

fun inferType(expression: ExpressionNode, context: TypeContext) : Type {
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

        override fun visit(node: FunctionCallNode): Type {
            val functionType = inferType(node.function, context)
            return when (functionType) {
                is FunctionType -> {
                    node.arguments.zip(functionType.arguments, { arg, argType -> verifyType(arg, context, expected = argType) })
                    if (functionType.arguments.size != node.arguments.size) {
                        throw WrongNumberOfArgumentsError(
                            expected = functionType.arguments.size,
                            actual = node.arguments.size,
                            source = node.source
                        )
                    }
                    functionType.returns
                }
                else -> {
                    val argumentTypes = node.arguments.map { argument -> inferType(argument, context) }
                    throw UnexpectedTypeError(
                        expected = FunctionType(argumentTypes, AnyType),
                        actual = functionType,
                        source = node.function.source
                    )
                }
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
