package org.shedlang.compiler.interpreter

import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.BinaryOperator
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.UnaryOperator
import org.shedlang.compiler.types.Symbol
import org.shedlang.compiler.types.SymbolType
import java.math.BigInteger
import java.util.*

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
            return expression.evaluate(name, context).map { this }
        }
        throw InterpreterError("Could not find module: " + name)
    }
}

internal data class VariableReference(val name: String): IncompleteExpression() {
    override fun evaluate(context: InterpreterContext): EvaluationResult<Expression> {
        return EvaluationResult.pure(context.value(name))
    }
}

internal data class UnaryOperation(
    val operator: UnaryOperator,
    val operand: Expression
): IncompleteExpression() {
    override fun evaluate(context: InterpreterContext): EvaluationResult<Expression> {
        return when (operand) {
            is IncompleteExpression -> {
                operand.evaluate(context).map { evaluatedOperand ->
                    UnaryOperation(
                        operator = operator,
                        operand = evaluatedOperand
                    )
                }
            }
            is InterpreterValue -> {
                when (operator) {
                    UnaryOperator.MINUS -> {
                        val integerOperand = operand as IntegerValue
                        return EvaluationResult.pure(IntegerValue(-integerOperand.value))
                    }
                    UnaryOperator.NOT -> {
                        val booleanOperand = operand as BooleanValue
                        return EvaluationResult.pure(BooleanValue(!booleanOperand.value))
                    }
                }
            }
        }
    }
}

internal data class BinaryOperation(
    val operator: BinaryOperator,
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
                    if (operator == BinaryOperator.EQUALS && left is BooleanValue && right is BooleanValue) {
                        EvaluationResult.pure(BooleanValue(left.value == right.value))
                    } else if (operator == BinaryOperator.AND && left is BooleanValue && right is BooleanValue) {
                        EvaluationResult.pure(BooleanValue(left.value && right.value))
                    } else if (operator == BinaryOperator.OR && left is BooleanValue && right is BooleanValue) {
                        EvaluationResult.pure(BooleanValue(left.value || right.value))

                    } else if (operator == BinaryOperator.EQUALS && left is IntegerValue && right is IntegerValue) {
                        EvaluationResult.pure(BooleanValue(left.value == right.value))
                    } else if (operator == BinaryOperator.ADD && left is IntegerValue && right is IntegerValue) {
                        EvaluationResult.pure(IntegerValue(left.value + right.value))
                    } else if (operator == BinaryOperator.SUBTRACT && left is IntegerValue && right is IntegerValue) {
                        EvaluationResult.pure(IntegerValue(left.value - right.value))
                    } else if (operator == BinaryOperator.MULTIPLY && left is IntegerValue && right is IntegerValue) {
                        EvaluationResult.pure(IntegerValue(left.value * right.value))

                    } else if (operator == BinaryOperator.EQUALS && left is StringValue && right is StringValue) {
                        EvaluationResult.pure(BooleanValue(left.value == right.value))
                    } else if (operator == BinaryOperator.ADD && left is StringValue && right is StringValue) {
                        EvaluationResult.pure(StringValue(left.value + right.value))

                    } else if (operator == BinaryOperator.EQUALS && left is CodePointValue && right is CodePointValue) {
                        EvaluationResult.pure(BooleanValue(left.value == right.value))
                    } else if (operator == BinaryOperator.LESS_THAN && left is CodePointValue && right is CodePointValue) {
                        EvaluationResult.pure(BooleanValue(left.value < right.value))
                    } else if (operator == BinaryOperator.LESS_THAN_OR_EQUAL && left is CodePointValue && right is CodePointValue) {
                        EvaluationResult.pure(BooleanValue(left.value <= right.value))
                    } else if (operator == BinaryOperator.GREATER_THAN && left is CodePointValue && right is CodePointValue) {
                        EvaluationResult.pure(BooleanValue(left.value > right.value))
                    } else if (operator == BinaryOperator.GREATER_THAN_OR_EQUAL && left is CodePointValue && right is CodePointValue) {
                        EvaluationResult.pure(BooleanValue(left.value >= right.value))

                    } else if (operator == BinaryOperator.EQUALS && left is SymbolValue && right is SymbolValue) {
                        EvaluationResult.pure(BooleanValue(left == right))

                    } else {
                        throw NotImplementedError(this.toString())
                    }
            }
        }
    }
}

internal fun call(
    receiver: Expression,
    positionalArgumentExpressions: List<Expression> = listOf(),
    positionalArgumentValues: List<InterpreterValue> = listOf(),
    namedArgumentExpressions: List<Pair<Identifier, Expression>> = listOf(),
    namedArgumentValues: List<Pair<Identifier, InterpreterValue>> = listOf()
): Call {
    return Call(
        receiver = receiver,
        positionalArgumentExpressions = positionalArgumentExpressions,
        positionalArgumentValues = positionalArgumentValues,
        namedArgumentExpressions = namedArgumentExpressions,
        namedArgumentValues = namedArgumentValues
    )
}

internal data class Call(
    val receiver: Expression,
    val positionalArgumentExpressions: List<Expression>,
    val positionalArgumentValues: List<InterpreterValue>,
    val namedArgumentExpressions: List<Pair<Identifier, Expression>>,
    val namedArgumentValues: List<Pair<Identifier, InterpreterValue>>
): IncompleteExpression() {
    override fun evaluate(context: InterpreterContext): EvaluationResult<Expression> {
        return when (receiver) {
            is IncompleteExpression -> receiver.evaluate(context).map { evaluatedReceiver ->
                copy(receiver = evaluatedReceiver)
            }
            is InterpreterValue -> {
                if (positionalArgumentExpressions.isNotEmpty()) {
                    val argument = positionalArgumentExpressions[0]
                    when (argument) {
                        is IncompleteExpression -> argument.evaluate(context).map { evaluatedArgument ->
                            copy(
                                positionalArgumentExpressions = listOf(evaluatedArgument) + positionalArgumentExpressions.drop(1)
                            )
                        }
                        is InterpreterValue -> EvaluationResult.pure(copy(
                            positionalArgumentExpressions = positionalArgumentExpressions.drop(1),
                            positionalArgumentValues = positionalArgumentValues + listOf(argument)
                        ))
                    }
                } else if (namedArgumentExpressions.isNotEmpty()) {
                    val (name, argument) = namedArgumentExpressions[0]
                    when (argument) {
                        is IncompleteExpression -> argument.evaluate(context).map { evaluatedArgument ->
                            copy(
                                namedArgumentExpressions = listOf(name to evaluatedArgument) + namedArgumentExpressions.drop(1)
                            )
                        }
                        is InterpreterValue -> EvaluationResult.pure(copy(
                            namedArgumentExpressions = namedArgumentExpressions.drop(1),
                            namedArgumentValues = namedArgumentValues + listOf(name to argument)
                        ))
                    }
                } else {
                    val arguments = Arguments(positionalArgumentValues, namedArgumentValues)
                    call(receiver, arguments, context)
                }
            }
        }
    }
}

private fun call(receiver: InterpreterValue, arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression> {
    return when (receiver) {
        is Callable ->
            receiver.call(arguments, context)
        else ->
            throw NotImplementedError()
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
                    is ModuleValue -> EvaluationResult.pure(receiver.fields.getValue(fieldName))
                    is ShapeValue -> EvaluationResult.pure(receiver.fields.getValue(fieldName))
                    else -> throw NotImplementedError()
                }
        }
    }
}

internal abstract class InterpreterValue: Expression()

internal object UnitValue: InterpreterValue()

internal data class BooleanValue(val value: Boolean): InterpreterValue()

internal data class IntegerValue(val value: BigInteger): InterpreterValue() {
    constructor(value: Int): this(value.toBigInteger())
}

internal fun InterpreterValue.int(): BigInteger {
    return (this as IntegerValue).value
}

internal data class StringValue(val value: String): InterpreterValue()

internal fun InterpreterValue.string(): String {
    return (this as StringValue).value
}

internal data class CodePointValue(val value: Int): InterpreterValue()

internal data class SymbolValue(private val value: Symbol): InterpreterValue()

internal data class ListValue(val elements: List<InterpreterValue>): InterpreterValue()

internal data class ModuleValue(val fields: Map<Identifier, InterpreterValue>) : InterpreterValue()

internal data class FunctionExpression(
    val positionalParameterNames: List<String>,
    val body: List<Statement>
): IncompleteExpression() {
    override fun evaluate(context: InterpreterContext): EvaluationResult<Expression> {
        return EvaluationResult.pure(FunctionValue(
            positionalParameterNames = positionalParameterNames,
            body = body,
            scope = context.scope
        ))
    }
}

internal data class FunctionValue(
    val positionalParameterNames: List<String>,
    val body: List<Statement>,
    val scope: Scope
): Callable() {
    override fun call(arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression> {
        val positionalBindings = positionalParameterNames.zip(arguments.positionals)
        val namedBindings = arguments.named.map { (name, argument) -> name.value to argument }

        return EvaluationResult.createStackFrame((positionalBindings + namedBindings).toMap()).map { frameId ->
            val scope = this.scope.enter(frameId)
            Block(
                body = body,
                scope = scope
            )
        }
    }
}

internal data class ShapeTypeValue(
    val constantFields: Map<Identifier, InterpreterValue>
): Callable() {
    override fun call(arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression> {
        val constantFields = constantFields.toMap()
        val dynamicFields = arguments.named.toMap()
        return EvaluationResult.pure(ShapeValue(constantFields + dynamicFields))
    }

}

internal object TypeAliasTypeValue: InterpreterValue()
internal object UnionTypeValue: InterpreterValue()

internal data class ShapeValue(
    val fields: Map<Identifier, InterpreterValue>
): InterpreterValue()

internal data class DeferredBlock(val body: List<Statement>): IncompleteExpression() {
    override fun evaluate(context: InterpreterContext): EvaluationResult<Expression> {
        return EvaluationResult.createStackFrame(mapOf()).map { frame ->
            Block(body, scope = context.scope.enter(frame))
        }
    }
}

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
            } else if (statement is Val && statement.expression is InterpreterValue) {
                return scope.add(statement.name, statement.expression, context).map { scope ->
                    Block(
                        body = body.drop(1),
                        scope = scope
                    )
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
            return EvaluationResult.createStackFrame(mapOf()).map { frame ->
                Block(elseBranch, scope = context.scope.enter(frame))
            }
        } else {
            val branch = conditionalBranches[0]
            return when (branch.condition) {
                BooleanValue(false) ->
                    EvaluationResult.pure(If(
                        conditionalBranches = conditionalBranches.drop(1),
                        elseBranch = elseBranch
                    ))

                BooleanValue(true) ->
                    EvaluationResult.createStackFrame(mapOf()).map { frame ->
                        Block(branch.body, scope = context.scope.enter(frame))
                    }

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

internal data class Val(val name: Identifier, val expression: Expression): Statement {
    override fun execute(context: InterpreterContext): EvaluationResult<Statement> {
        when (expression) {
            is IncompleteExpression ->
                return expression.evaluate(context).map { evaluatedExpression ->
                    Val(name, evaluatedExpression)
                }
        }
        throw UnsupportedOperationException("not implemented")
    }
}

internal data class Arguments(
    val positionals: List<InterpreterValue>,
    val named: List<Pair<Identifier, InterpreterValue>>
) {
    operator fun get(index: Int): InterpreterValue {
        return positionals[index]
    }

    operator fun plus(other: Arguments): Arguments {
        return Arguments(
            positionals + other.positionals,
            named + other.named
        )
    }

    fun dropPositional(count: Int): Arguments {
        return Arguments(
            positionals.drop(count),
            named
        )
    }
}

internal abstract class Callable: InterpreterValue() {
    abstract fun call(arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression>
}

internal object IntToStringValue: Callable() {
    override fun call(arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression> {
        val argument = arguments[0].int()
        return EvaluationResult.pure(StringValue(argument.toString()))
    }
}

internal object ListConstructorValue: Callable() {
    override fun call(arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression> {
        return EvaluationResult.pure(ListValue(arguments.positionals))
    }
}

internal object PrintValue: Callable() {
    override fun call(arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression> {
        val argument = arguments[0].string()
        return EvaluationResult.stdout(argument)
    }
}

internal object PartialCallFunctionValue : Callable() {
    override fun call(arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression> {
        return EvaluationResult.pure(PartialCallValue(
            receiver = arguments[0],
            partialArguments = arguments.dropPositional(1)
        ))
    }
}

internal data class PartialCallValue(
    private val receiver: Expression,
    private val partialArguments: Arguments
): Callable() {
    override fun call(arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression> {
        val completeArguments = partialArguments + arguments
        return EvaluationResult.pure(Call(
            receiver = receiver,
            positionalArgumentExpressions = listOf(),
            positionalArgumentValues = completeArguments.positionals,
            namedArgumentExpressions = listOf(),
            namedArgumentValues = completeArguments.named
        ))
    }
}

internal class InterpreterContext(
    internal val scope: Scope,
    private val scopeFrames: WeakHashMap<ScopeFrameId, ScopeFrame>,
    private val moduleValues: Map<List<Identifier>, ModuleValue>,
    private val moduleExpressions: Map<List<Identifier>, ModuleExpression>
) {
    fun value(name: String): InterpreterValue {
        return scope.value(name, this)
    }

    fun moduleValue(name: List<Identifier>): ModuleValue? {
        return moduleValues[name]
    }

    fun moduleExpression(name: List<Identifier>): ModuleExpression? {
        return moduleExpressions[name]
    }

    fun scopeFrame(frameId: ScopeFrameId): ScopeFrame {
        return scopeFrames[frameId]!!
    }

    fun inScope(scope: Scope): InterpreterContext {
        return InterpreterContext(
            scope = scope,
            scopeFrames = scopeFrames,
            moduleExpressions = moduleExpressions,
            moduleValues = moduleValues
        )
    }

    fun updateModule(moduleName: List<Identifier>, module: ModuleExpression): InterpreterContext {
        return InterpreterContext(
            scope = scope,
            scopeFrames = scopeFrames,
            moduleExpressions = moduleExpressions + mapOf(moduleName to module),
            moduleValues = moduleValues
        )
    }

    fun updateModule(moduleName: List<Identifier>, module: ModuleValue): InterpreterContext {
        return InterpreterContext(
            scope = scope,
            scopeFrames = scopeFrames,
            moduleExpressions = moduleExpressions.filterKeys { key -> key != moduleName },
            moduleValues = moduleValues + mapOf(moduleName to module)
        )
    }

    fun updateScopeFrame(frameId: ScopeFrameId, frame: ScopeFrame): InterpreterContext {
        return InterpreterContext(
            scope = scope,
            scopeFrames = WeakHashMap(scopeFrames + mapOf(frameId to frame)),
            moduleExpressions = moduleExpressions,
            moduleValues = moduleValues
        )
    }
}

var nextScopeFrameId = 1

internal fun createScopeFrameId() = ScopeFrameId(nextScopeFrameId++)

internal data class ScopeFrameId(private val id: Int)

internal data class Scope(private val frames: List<ScopeFrame>) {
    fun value(name: String, context: InterpreterContext): InterpreterValue {
        for (frame in frames) {
            val value = frame.value(name, context)
            if (value != null) {
                return value
            }
        }
        throw InterpreterError("Could not find variable: " + name)
    }

    fun add(name: Identifier, value: InterpreterValue, context: InterpreterContext): EvaluationResult<Scope> {
        return frames[0].add(name, value, context).map { frame ->
            Scope(listOf(frame) + frames.drop(1))
        }
    }

    fun enter(frame: ScopeFrame): Scope {
        return Scope(frames = listOf(frame) + frames)
    }
}

internal interface ScopeFrame {
    fun value(name: String, context: InterpreterContext): InterpreterValue?
    fun add(name: Identifier, value: InterpreterValue, context: InterpreterContext): EvaluationResult<ScopeFrame>

    companion object {
        // TODO: Remove?
        val EMPTY = ScopeFrameMap(mapOf())
    }
}

internal data class ScopeFrameMap(private val variables: Map<String, InterpreterValue>): ScopeFrame {
    override fun value(name: String, context: InterpreterContext): InterpreterValue? {
        return variables[name]
    }

    override fun add(name: Identifier, value: InterpreterValue, context: InterpreterContext): EvaluationResult<ScopeFrame> {
        if (variables.containsKey(name.value)) {
            throw InterpreterError("name is already bound in scope: " + name.value)
        } else {
            return EvaluationResult.pure(ScopeFrameMap(mapOf(name.value to value) + variables))
        }
    }
}

internal data class IndirectScopeFrame(private val frameId: ScopeFrameId): ScopeFrame {
    override fun value(name: String, context: InterpreterContext): InterpreterValue? {
        return context.scopeFrame(frameId).value(name, context)
    }

    override fun add(name: Identifier, value: InterpreterValue, context: InterpreterContext): EvaluationResult<ScopeFrame> {
        return context.scopeFrame(frameId).add(name, value, context).flatMap { frame ->
            EvaluationResult.updateStackFrame(frameId, frame)
        }.map { this }
    }

}

internal data class ModuleScopeFrame(
    private val moduleName: List<Identifier>
): ScopeFrame {
    override fun value(name: String, context: InterpreterContext): InterpreterValue? {
        val identifier = Identifier(name)

        val moduleValue = context.moduleValue(moduleName)
        if (moduleValue != null) {
            return moduleValue.fields[identifier]
        }

        val moduleExpression = context.moduleExpression(moduleName)
        if (moduleExpression != null) {
            val value = moduleExpression.fieldValues.toMap()[identifier]
            if (value != null) {
                return value
            }

            return null
        }

        throw InterpreterError("Could not find module: " + moduleName)
    }

    override fun add(name: Identifier, value: InterpreterValue, context: InterpreterContext): EvaluationResult<ScopeFrame> {
        throw UnsupportedOperationException("not implemented")
    }

}

internal val builtinStackFrame = ScopeFrameMap(mapOf(
    "intToString" to IntToStringValue,
    "list" to ListConstructorValue,
    "print" to PrintValue
))

fun fullyEvaluate(modules: ModuleSet, moduleName: List<Identifier>): ModuleEvaluationResult {
    val loadedModules = loadModuleSet(modules)
    val call = Call(
        receiver = FieldAccess(ModuleReference(moduleName), Identifier("main")),
        positionalArgumentExpressions = listOf(),
        positionalArgumentValues = listOf(),
        namedArgumentExpressions = listOf(),
        namedArgumentValues = listOf()
    )
    val context = InterpreterContext(
        scope = Scope(listOf()),
        moduleExpressions = loadedModules,
        moduleValues = mapOf(),
        scopeFrames = WeakHashMap()
    )
    val result = fullyEvaluate(call, context)
    val exitCode = when (result.value) {
        is IntegerValue -> result.value.value.toInt()
        is UnitValue -> 0
        else -> throw NotImplementedError()
    }
    return ModuleEvaluationResult(exitCode = exitCode, stdout = result.stdout)
}

data class ModuleEvaluationResult(val exitCode: Int, val stdout: String)

internal data class FullEvaluationResult(val value: InterpreterValue, val stdout: String)

internal fun fullyEvaluate(initialExpression: Expression, initialContext: InterpreterContext): FullEvaluationResult {
    var expression = initialExpression
    var context = initialContext
    val stdout = StringBuilder()
    while (true) {
        when (expression) {
            is IncompleteExpression -> {
                val result = expression.evaluate(context)
                stdout.append(result.stdout)
                expression = result.value

                for ((moduleName, moduleValue) in result.moduleValueUpdates) {
                    context = context.updateModule(moduleName, moduleValue)
                }
                for ((moduleName, moduleExpression) in result.moduleExpressionUpdates) {
                    context = context.updateModule(moduleName, moduleExpression)
                }
                for ((frameId, frame) in result.scopeFrameUpdates) {
                    context = context.updateScopeFrame(frameId, frame)
                }
            }
            is InterpreterValue ->
                return FullEvaluationResult(value = expression, stdout = stdout.toString())
        }
    }
}

private fun moduleStackFrames(
    moduleName: List<Identifier>?
): List<ScopeFrame> {
    val moduleFrames = if (moduleName == null) {
        listOf()
    } else {
        val moduleNameString = moduleName.map(Identifier::value).joinToString(".")
        listOf(
            ModuleScopeFrame(moduleName),
            ScopeFrameMap(mapOf("moduleName" to StringValue(moduleNameString)))
        )
    }
    return moduleFrames + listOf(builtinStackFrame)
}

internal data class EvaluationResult<out T>(
    val value: T,
    val stdout: String,
    val moduleValueUpdates: List<Pair<List<Identifier>, ModuleValue>>,
    val moduleExpressionUpdates: List<Pair<List<Identifier>, ModuleExpression>>,
    val scopeFrameUpdates: List<Pair<ScopeFrameId, ScopeFrame>>
) {
    internal fun <R> map(func: (T) -> R): EvaluationResult<R> {
        return EvaluationResult(
            func(value),
            stdout = stdout,
            moduleValueUpdates = moduleValueUpdates,
            moduleExpressionUpdates = moduleExpressionUpdates,
            scopeFrameUpdates = scopeFrameUpdates
        )
    }

    internal fun <R> flatMap(func: (T) -> EvaluationResult<R>): EvaluationResult<R> {
        val result = func(value)
        return EvaluationResult(
            result.value,
            stdout = stdout + result.stdout,
            moduleValueUpdates = moduleValueUpdates + result.moduleValueUpdates,
            moduleExpressionUpdates = moduleExpressionUpdates + result.moduleExpressionUpdates,
            scopeFrameUpdates = scopeFrameUpdates + result.scopeFrameUpdates
        )
    }

    companion object {
        internal fun <T> pure(value: T): EvaluationResult<T> {
            return EvaluationResult(
                value,
                stdout = "",
                moduleValueUpdates = listOf(),
                moduleExpressionUpdates = listOf(),
                scopeFrameUpdates = listOf()
            )
        }

        internal fun updateModuleValue(moduleName: List<Identifier>, moduleValue: ModuleValue): EvaluationResult<Unit> {
            return EvaluationResult(
                Unit,
                stdout = "",
                moduleValueUpdates = listOf(moduleName to moduleValue),
                moduleExpressionUpdates = listOf(),
                scopeFrameUpdates = listOf()
            )
        }

        internal fun updateModuleExpression(moduleName: List<Identifier>, moduleExpression: ModuleExpression): EvaluationResult<Unit> {
            return EvaluationResult(
                Unit,
                stdout = "",
                moduleValueUpdates = listOf(),
                moduleExpressionUpdates = listOf(moduleName to moduleExpression),
                scopeFrameUpdates = listOf()
            )
        }

        internal fun stdout(stdout: String): EvaluationResult<UnitValue> {
            return EvaluationResult(
                UnitValue,
                stdout = stdout,
                moduleValueUpdates = listOf(),
                moduleExpressionUpdates = listOf(),
                scopeFrameUpdates = listOf()
            )
        }

        internal fun createStackFrame(variables: Map<String, InterpreterValue>): EvaluationResult<ScopeFrame> {
            val frameId = createScopeFrameId()
            val frame = ScopeFrameMap(variables)
            return updateStackFrame(
                frameId,
                frame
            ).map { IndirectScopeFrame(frameId) }
        }

        internal fun updateStackFrame(frameId: ScopeFrameId, frame: ScopeFrame): EvaluationResult<Unit> {
            return EvaluationResult(
                Unit,
                stdout = "",
                moduleValueUpdates = listOf(),
                moduleExpressionUpdates = listOf(),
                scopeFrameUpdates = listOf(frameId to frame)
            )
        }
    }
}

internal class ModuleStatement(
    val expression: Expression,
    val bindings: (expression: InterpreterValue) -> List<Pair<Identifier, InterpreterValue>>
) {
    internal fun withExpression(expression: Expression): ModuleStatement = ModuleStatement(
        expression = expression,
        bindings = bindings
    )

    companion object {
        internal fun declaration(name: Identifier, expression: Expression) = ModuleStatement(
            expression = expression,
            bindings = { value -> listOf(name to value) }
        )
    }
}

internal data class ModuleExpression(
    val statements: List<ModuleStatement>,
    val fieldValues: List<Pair<Identifier, InterpreterValue>>
) {
    fun evaluate(moduleName: List<Identifier>, context: InterpreterContext): EvaluationResult<Unit> {
        if (statements.isEmpty()) {
            return EvaluationResult.updateModuleValue(moduleName, ModuleValue(fieldValues.toMap()))
        } else {
            val statement = statements[0]
            val expression = statement.expression

            when (expression) {
                is InterpreterValue ->
                    return EvaluationResult.updateModuleExpression(
                        moduleName,
                        ModuleExpression(
                            statements = statements.drop(1),
                            fieldValues = fieldValues + statement.bindings(expression)
                        )
                    )
                is IncompleteExpression -> {
                    val scope = Scope(
                        frames = moduleStackFrames(moduleName)
                    )
                    return expression.evaluate(context.inScope(scope)).flatMap { value ->
                        EvaluationResult.updateModuleExpression(
                            moduleName,
                            ModuleExpression(
                                statements = listOf(statement.withExpression(value)) + statements.drop(1),
                                fieldValues = fieldValues
                            )
                        )
                    }
                }

            }
        }
    }
}

internal fun symbolTypeToValue(symbolType: SymbolType): SymbolValue {
    return SymbolValue(symbolType.symbol)
}
