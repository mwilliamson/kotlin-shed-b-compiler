package org.shedlang.compiler.interpreter

import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.ExpressionNode
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.Operator

internal class InterpreterError(message: String): Exception(message)

internal sealed class Expression

internal abstract class IncompleteExpression: Expression() {
    abstract fun evaluate(context: InterpreterContext): EvaluationResult<Expression>
}

internal data class ModuleReference(val name: List<Identifier>): IncompleteExpression() {
    override fun evaluate(context: InterpreterContext): EvaluationResult<Expression> {
        val value = context.moduleValue(name)
        if (value != null) {
            return EvaluationResult.pure(value)
        }
        val expression = context.moduleExpression(name)
        if (expression != null) {
            return EvaluationResult.ModuleUpdate(name)
        }
        throw InterpreterError("Could not find module: " + name)
    }
}

internal data class VariableReference(val name: String): IncompleteExpression() {
    override fun evaluate(context: InterpreterContext): EvaluationResult<Expression> {
        return EvaluationResult.pure(context.value(name))
    }
}

internal data class BinaryOperation(
    val operator: Operator,
    val left: Expression,
    val right: Expression
): IncompleteExpression() {
    override fun evaluate(context: InterpreterContext): EvaluationResult<Expression> {
        return when (left) {
            is IncompleteExpression -> left.evaluate(context).map { evaluatedLeft ->
                BinaryOperation(
                    operator,
                    evaluatedLeft,
                    right
                )
            }
            is InterpreterValue -> when (right) {
                is IncompleteExpression -> right.evaluate(context).map { evaluatedRight ->
                    BinaryOperation(
                        operator,
                        left,
                        evaluatedRight
                    )
                }
                is InterpreterValue ->
                    if (operator == Operator.EQUALS && left is IntegerValue && right is IntegerValue) {
                        EvaluationResult.pure(BooleanValue(left.value == right.value))
                    } else if (operator == Operator.ADD && left is IntegerValue && right is IntegerValue) {
                        EvaluationResult.pure(IntegerValue(left.value + right.value))
                    } else if (operator == Operator.SUBTRACT && left is IntegerValue && right is IntegerValue) {
                        EvaluationResult.pure(IntegerValue(left.value - right.value))
                    } else if (operator == Operator.MULTIPLY && left is IntegerValue && right is IntegerValue) {
                        EvaluationResult.pure(IntegerValue(left.value * right.value))
                    } else if (operator == Operator.EQUALS && left is StringValue && right is StringValue) {
                        EvaluationResult.pure(BooleanValue(left.value == right.value))
                    } else if (operator == Operator.ADD && left is StringValue && right is StringValue) {
                        EvaluationResult.pure(StringValue(left.value + right.value))
                    } else {
                        throw NotImplementedError()
                    }
            }
        }
    }
}

internal data class Call(
    val receiver: Expression,
    val positionalArgumentExpressions: List<Expression>,
    val positionalArgumentValues: List<InterpreterValue>
): IncompleteExpression() {
    override fun evaluate(context: InterpreterContext): EvaluationResult<Expression> {
        return when (receiver) {
            is IncompleteExpression -> receiver.evaluate(context).map { evaluatedReceiver ->
                Call(
                    evaluatedReceiver,
                    positionalArgumentExpressions,
                    positionalArgumentValues
                )
            }
            is InterpreterValue -> {
                if (positionalArgumentExpressions.isNotEmpty()) {
                    val argument = positionalArgumentExpressions[0]
                    when (argument) {
                        is IncompleteExpression -> argument.evaluate(context).map { evaluatedArgument ->
                            Call(
                                receiver,
                                listOf(evaluatedArgument) + positionalArgumentExpressions.drop(1),
                                positionalArgumentValues
                            )
                        }
                        is InterpreterValue -> EvaluationResult.pure(Call(
                            receiver,
                            positionalArgumentExpressions.drop(1),
                            positionalArgumentValues + listOf(argument)
                        ))
                    }
                } else {
                    call(receiver, positionalArgumentValues, context)
                }
            }
        }
    }
}

private fun call(
    receiver: InterpreterValue,
    positionalArguments: List<InterpreterValue>,
    context: InterpreterContext
): EvaluationResult<Expression> {
    return when (receiver) {
        is IntToStringValue -> {
            val argument = (positionalArguments[0] as IntegerValue).value
            EvaluationResult.pure(StringValue(argument.toString()))
        }
        is PrintValue -> {
            val argument = (positionalArguments[0] as StringValue).value
            EvaluationResult.Value(UnitValue, stdout = argument)
        }
        is FunctionValue -> {
            val moduleFields = if (receiver.moduleName == null) {
                mapOf()
            } else {
                val moduleValue = context.moduleValue(receiver.moduleName)!!
                moduleValue.fields
            }
            val scope = Scope(listOf(
                ScopeFrame(receiver.positionalParameterNames.zip(positionalArguments).toMap()),
                ScopeFrame(moduleFields.mapKeys { key -> key.key.value }),
                builtinStackFrame
            ))
            EvaluationResult.pure(Block(
                body = receiver.body,
                scope = scope
            ))
        }
        else -> throw NotImplementedError()
    }
}

internal data class FieldAccess(
    val receiver: Expression,
    val fieldName: Identifier
): IncompleteExpression() {
    override fun evaluate(context: InterpreterContext): EvaluationResult<Expression> {
        return when (receiver) {
            is IncompleteExpression -> receiver.evaluate(context).map { evaluatedReceiver ->
                FieldAccess(evaluatedReceiver, fieldName)
            }
            is InterpreterValue ->
                when (receiver) {
                    is ModuleValue -> EvaluationResult.pure(receiver.fields[fieldName]!!)
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
internal data class FunctionValue(
    val positionalParameterNames: List<String>,
    val body: List<Statement>,
    val moduleName: List<Identifier>?
): InterpreterValue()

internal data class Block(val body: List<Statement>, val scope: Scope): IncompleteExpression() {
    override fun evaluate(context: InterpreterContext): EvaluationResult<Expression> {
        if (body.isEmpty()) {
            return EvaluationResult.pure(UnitValue)
        } else {
            val statement = body[0]
            if (statement is ExpressionStatement && statement.expression is InterpreterValue) {
                if (statement.isReturn) {
                    return EvaluationResult.pure(statement.expression)
                } else {
                    return EvaluationResult.pure(withBody(body.drop(1)))
                }
            } else {
                return statement.execute(context.inScope(scope)).map { evaluatedStatement ->
                    withBody(listOf(evaluatedStatement) + body.drop(1))
                }
            }
        }
    }

    private fun withBody(body: List<Statement>): Block {
        return Block(body, scope)
    }
}

internal data class If(
    val conditionalBranches: List<ConditionalBranch>,
    val elseBranch: List<Statement>
): IncompleteExpression() {
    override fun evaluate(context: InterpreterContext): EvaluationResult<Expression> {
        if (conditionalBranches.isEmpty()) {
            // TODO: extend scope
            return EvaluationResult.pure(Block(elseBranch, scope = context.scope))
        } else {
            val branch = conditionalBranches[0]
            return when (branch.condition) {
                BooleanValue(false) ->
                    EvaluationResult.pure(If(
                        conditionalBranches = conditionalBranches.drop(1),
                        elseBranch = elseBranch
                    ))

                BooleanValue(true) ->
                    // TODO: extend scope
                    EvaluationResult.pure(Block(branch.body, scope = context.scope))

                is IncompleteExpression ->
                   branch.condition.evaluate(context).map { evaluatedCondition ->
                       If(
                           conditionalBranches = listOf(
                               ConditionalBranch(
                                   condition = evaluatedCondition,
                                   body = branch.body
                               )
                           ) + conditionalBranches.drop(1),
                           elseBranch = elseBranch
                       )
                   }

                else ->
                    throw NotImplementedError()
            }
        }
    }

}

internal data class ConditionalBranch(
    val condition: Expression,
    val body: List<Statement>
)

internal interface Statement {
    fun execute(context: InterpreterContext): EvaluationResult<Statement>
}

internal data class ExpressionStatement(val expression: Expression, val isReturn: Boolean): Statement {
    override fun execute(context: InterpreterContext): EvaluationResult<Statement> {
        if (expression is IncompleteExpression) {
            return expression.evaluate(context).map { evaluatedExpression ->
                ExpressionStatement(evaluatedExpression, isReturn = isReturn)
            }
        } else {
            throw NotImplementedError()
        }
    }
}

internal object IntToStringValue: InterpreterValue()
internal object PrintValue: InterpreterValue()

internal class InterpreterContext(
    internal val scope: Scope,
    private val moduleValues: Map<List<Identifier>, ModuleValue>,
    private val moduleExpressions: Map<List<Identifier>, ModuleExpression>
) {
    fun value(name: String): InterpreterValue {
        return scope.value(name)
    }

    fun moduleValue(name: List<Identifier>): ModuleValue? {
        return moduleValues[name]
    }

    fun moduleExpression(name: List<Identifier>): ModuleExpression? {
        return moduleExpressions[name]
    }

    fun inScope(scope: Scope): InterpreterContext {
        return InterpreterContext(
            scope = scope,
            moduleExpressions = moduleExpressions,
            moduleValues = moduleValues
        )
    }

    fun updateModule(moduleName: List<Identifier>, module: ModuleExpression): InterpreterContext {
        return InterpreterContext(
            scope = scope,
            moduleExpressions = moduleExpressions + mapOf(moduleName to module),
            moduleValues = moduleValues
        )
    }

    fun updateModule(moduleName: List<Identifier>, module: ModuleValue): InterpreterContext {

        return InterpreterContext(
            scope = scope,
            moduleExpressions = moduleExpressions.filterKeys { key -> key != moduleName },
            moduleValues = moduleValues + mapOf(moduleName to module)
        )
    }
}

internal data class Scope(private val frames: List<ScopeFrame>) {
    fun value(name: String): InterpreterValue {
        for (frame in frames) {
            val value = frame.value(name)
            if (value != null) {
                return value
            }
        }
        throw InterpreterError("Could not find variable: " + name)
    }
}

internal data class ScopeFrame(private val variables: Map<String, InterpreterValue>) {
    fun value(name: String): InterpreterValue? {
        return variables[name]
    }
}

internal val builtinStackFrame = ScopeFrame(mapOf(
    "intToString" to IntToStringValue,
    "print" to PrintValue
))

internal fun evaluate(modules: ModuleSet, moduleName: List<Identifier>): ModuleEvaluationResult {
    val loadedModules = loadModuleSet(modules)
    val call = Call(
        receiver = FieldAccess(ModuleReference(moduleName), Identifier("main")),
        positionalArgumentExpressions = listOf(),
        positionalArgumentValues = listOf()
    )
    val context = InterpreterContext(
        scope = Scope(listOf(builtinStackFrame)),
        moduleExpressions = loadedModules,
        moduleValues = mapOf()
    )
    val result = evaluate(call, context)
    val exitCode = when (result.value) {
        is IntegerValue -> result.value.value
        is UnitValue -> 0
        else -> throw NotImplementedError()
    }
    return ModuleEvaluationResult(exitCode = exitCode, stdout = result.stdout)
}

internal data class ModuleEvaluationResult(val exitCode: Int, val stdout: String)

internal fun evaluate(expressionNode: ExpressionNode, context: InterpreterContext): EvaluationResult<InterpreterValue> {
    return evaluate(loadExpression(expressionNode), context)
}

internal fun evaluate(initialExpression: Expression, initialContext: InterpreterContext): EvaluationResult.Value<InterpreterValue> {
    var expression = initialExpression
    var context = initialContext
    val stdout = StringBuilder()
    while (true) {
        when (expression) {
            is IncompleteExpression -> {
                val result = expression.evaluate(context)
                when (result) {
                    is EvaluationResult.Value -> {
                        stdout.append(result.stdout)
                        expression = result.value
                    }
                    is EvaluationResult.ModuleUpdate -> {
                        context = updateModule(result.moduleName, context)
                    }
                }
            }
            is InterpreterValue ->
                return EvaluationResult.Value(value = expression, stdout = stdout.toString())
        }
    }
}

private fun updateModule(moduleName: List<Identifier>, context: InterpreterContext): InterpreterContext {
    val moduleExpression = context.moduleExpression(moduleName)!!
    return moduleExpression.evaluate(moduleName, context)
}

internal sealed class EvaluationResult<out T> {
    internal abstract fun <R> map(func: (T) -> R): EvaluationResult<R>

    internal data class Value<out T>(val value: T, val stdout: String): EvaluationResult<T>() {
        override fun <R> map(func: (T) -> R): EvaluationResult<R> {
            return EvaluationResult.Value(func(value), stdout)
        }
    }

    internal data class ModuleUpdate(val moduleName: List<Identifier>): EvaluationResult<Nothing>() {
        override fun <R> map(func: (Nothing) -> R): EvaluationResult<R> {
            return this
        }
    }

    companion object {
        internal fun <T> pure(value: T): EvaluationResult<T> {
            return EvaluationResult.Value(value, stdout = "")
        }
    }
}

internal data class ModuleExpression(
    val fieldExpressions: List<Pair<Identifier, Expression>>,
    val fieldValues: List<Pair<Identifier, InterpreterValue>>
) {
    fun evaluate(moduleName: List<Identifier>, context: InterpreterContext): InterpreterContext {
        if (fieldExpressions.isEmpty()) {
            return context.updateModule(moduleName, ModuleValue(fieldValues.toMap()))
        } else {
            val (fieldName, expression) = fieldExpressions[0]
            when (expression) {
                is InterpreterValue ->
                    return context.updateModule(moduleName, ModuleExpression(
                        fieldExpressions = fieldExpressions.drop(1),
                        fieldValues = fieldValues + listOf(fieldName to expression)
                    ))
                is IncompleteExpression -> {
                    val result = expression.evaluate(context)
                    when (result) {
                        is EvaluationResult.Value -> {
                            if (result.stdout != "") {
                                throw InterpreterError("Unexpected side effect")
                            }
                            return context.updateModule(moduleName, ModuleExpression(
                                fieldExpressions = listOf(fieldName to result.value) + fieldExpressions.drop(1),
                                fieldValues = fieldValues
                            ))
                        }
                        is EvaluationResult.ModuleUpdate -> {
                            return updateModule(result.moduleName, context)
                        }
                    }
                }

            }
        }
    }
}
