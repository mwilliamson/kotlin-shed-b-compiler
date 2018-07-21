package org.shedlang.compiler.interpreter

import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.ExpressionNode
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.Operator

internal sealed class Expression

internal abstract class IncompleteExpression: Expression() {
    abstract fun evaluate(context: InterpreterContext): Expression
}

internal data class ModuleReference(val name: List<Identifier>): IncompleteExpression() {
    override fun evaluate(context: InterpreterContext): Expression {
        return context.module(name)
    }
}

internal data class VariableReference(val name: String): IncompleteExpression() {
    override fun evaluate(context: InterpreterContext): Expression {
        return context.value(name)
    }
}

internal data class BinaryOperation(
    val operator: Operator,
    val left: Expression,
    val right: Expression
): IncompleteExpression() {
    override fun evaluate(context: InterpreterContext): Expression {
        return when (left) {
            is IncompleteExpression -> BinaryOperation(
                operator,
                left.evaluate(context),
                right
            )
            is InterpreterValue -> when (right) {
                is IncompleteExpression -> BinaryOperation(
                    operator,
                    left,
                    right.evaluate(context)
                )
                is InterpreterValue ->
                    if (operator == Operator.EQUALS && left is IntegerValue && right is IntegerValue) {
                        BooleanValue(left.value == right.value)
                    } else if (operator == Operator.ADD && left is IntegerValue && right is IntegerValue) {
                        IntegerValue(left.value + right.value)
                    } else if (operator == Operator.SUBTRACT && left is IntegerValue && right is IntegerValue) {
                        IntegerValue(left.value - right.value)
                    } else if (operator == Operator.MULTIPLY && left is IntegerValue && right is IntegerValue) {
                        IntegerValue(left.value * right.value)
                    } else if (operator == Operator.EQUALS && left is StringValue && right is StringValue) {
                        BooleanValue(left.value == right.value)
                    } else if (operator == Operator.ADD && left is StringValue && right is StringValue) {
                        StringValue(left.value + right.value)
                    } else {
                        throw NotImplementedError()
                    }
            }
        }
    }
}

internal data class Call(
    val receiver: Expression,
    val positionalArguments: List<Expression>
): IncompleteExpression() {
    override fun evaluate(context: InterpreterContext): Expression {
        return when (receiver) {
            is IncompleteExpression -> Call(
                receiver.evaluate(context),
                positionalArguments
            )
            is InterpreterValue -> call(receiver, positionalArguments as List<InterpreterValue>, context)
        }
    }
}

private fun call(
    receiver: InterpreterValue,
    positionalArguments: List<InterpreterValue>,
    context: InterpreterContext
): Expression {
    return when (receiver) {
        is PrintValue -> {
            context.writeStdout((positionalArguments[0] as StringValue).value)
            UnitValue
        }
        is FunctionValue -> Block(
            body = receiver.body
        )
        else -> throw NotImplementedError()
    }
}

internal data class FieldAccess(
    val receiver: Expression,
    val fieldName: Identifier
): IncompleteExpression() {
    override fun evaluate(context: InterpreterContext): Expression {
        return when (receiver) {
            is IncompleteExpression -> FieldAccess(
                receiver.evaluate(context),
                fieldName
            )
            is InterpreterValue ->
                when (receiver) {
                    is ModuleValue -> receiver.fields[fieldName]!!
                    else -> throw NotImplementedError()
                }
        }
    }
}

internal abstract class InterpreterValue: Expression()
internal object UnitValue: InterpreterValue()
internal data class BooleanValue(val value: Boolean): InterpreterValue()
internal data class IntegerValue(val value: Int): InterpreterValue()
internal data class StringValue(val value: String): InterpreterValue()
internal data class CharacterValue(val value: Int): InterpreterValue()
internal data class SymbolValue(val name: String): InterpreterValue()

internal data class ModuleValue(val fields: Map<Identifier, InterpreterValue>) : InterpreterValue()
internal data class FunctionValue(val body: List<Statement>): InterpreterValue()

internal data class Block(val body: List<Statement>): IncompleteExpression() {
    override fun evaluate(context: InterpreterContext): Expression {
        if (body.isEmpty()) {
            return UnitValue
        } else {
            val statement = body[0]
            if (statement is ExpressionStatement && statement.expression is InterpreterValue) {
                if (statement.isReturn) {
                    return statement.expression
                } else {
                    return Block(
                        body.drop(1)
                    )
                }
            } else {
                return Block(
                    listOf(statement.execute(context)) + body.drop(1)
                )
            }
        }
    }
}

internal data class If(
    val conditionalBranches: List<ConditionalBranch>,
    val elseBranch: List<Statement>
): IncompleteExpression() {
    override fun evaluate(context: InterpreterContext): Expression {
        throw UnsupportedOperationException("not implemented")
    }

}

internal data class ConditionalBranch(
    val condition: Expression,
    val body: List<Statement>
)

internal interface Statement {
    fun execute(context: InterpreterContext): Statement
}

internal data class ExpressionStatement(val expression: Expression, val isReturn: Boolean): Statement {
    override fun execute(context: InterpreterContext): Statement {
        if (expression is IncompleteExpression) {
            return ExpressionStatement(expression.evaluate(context), isReturn)
        } else {
            throw NotImplementedError()
        }
    }
}

internal object PrintValue: InterpreterValue()

internal class InterpreterContext(
    private val variables: Map<String, InterpreterValue>,
    private val modules: Map<List<Identifier>, ModuleValue>
) {
    private val stdoutBuilder = StringBuilder()

    fun value(name: String): InterpreterValue {
        return variables[name]!!
    }

    fun module(name: List<Identifier>): InterpreterValue {
        return modules[name]!!
    }

    val stdout: String
        get() = stdoutBuilder.toString()

    fun writeStdout(value: String) {
        stdoutBuilder.append(value)
    }
}

internal fun evaluate(modules: ModuleSet, moduleName: List<Identifier>): EvaluationResult {
    val loadedModules = loadModuleSet(modules)
    val call = Call(
        receiver = FieldAccess(ModuleReference(moduleName), Identifier("main")),
        positionalArguments = listOf()
    )
    val context = InterpreterContext(variables = mapOf(), modules = loadedModules)
    val result = evaluate(call, context)
    val exitCode = when (result) {
        is IntegerValue -> result.value
        is UnitValue -> 0
        else -> throw NotImplementedError()
    }
    return EvaluationResult(exitCode = exitCode, stdout = context.stdout)
}

internal data class EvaluationResult(val exitCode: Int, val stdout: String)

internal fun evaluate(expressionNode: ExpressionNode, context: InterpreterContext): InterpreterValue {
    return evaluate(loadExpression(expressionNode), context)
}

internal fun evaluate(initialExpression: Expression, context: InterpreterContext): InterpreterValue {
    var expression = initialExpression
    while (true) {
        when (expression) {
            is IncompleteExpression ->
                expression = expression.evaluate(context)
            is InterpreterValue ->
                return expression
        }
    }
}
