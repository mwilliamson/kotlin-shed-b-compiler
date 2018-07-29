package org.shedlang.compiler.interpreter

import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.Operator
import org.shedlang.compiler.types.ShapeType
import org.shedlang.compiler.types.SymbolType
import org.shedlang.compiler.types.Type
import org.shedlang.compiler.types.findDiscriminator

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
            return expression.evaluate(name, context)
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
                    } else if (operator == Operator.EQUALS && left is SymbolValue && right is SymbolValue) {
                        EvaluationResult.pure(BooleanValue(left == right))
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
                    call(receiver, positionalArgumentValues, namedArgumentValues)
                }
            }
        }
    }
}

private fun call(
    receiver: InterpreterValue,
    positionalArguments: List<InterpreterValue>,
    namedArguments: List<Pair<Identifier, InterpreterValue>>
): EvaluationResult<Expression> {
    return when (receiver) {
        is Callable ->
            receiver.call(positionalArguments, namedArguments)
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
internal data class IntegerValue(val value: Int): InterpreterValue()
internal data class StringValue(val value: String): InterpreterValue()
internal data class CharacterValue(val value: Int): InterpreterValue()
internal data class SymbolValue(
    val moduleName: List<Identifier>,
    val name: String
): InterpreterValue()
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
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        val positionalBindings = positionalParameterNames.zip(positionalArguments)
        val namedBindings = namedArguments.map { (name, argument) -> name.value to argument }
        val scope = this.scope.enter(
            ScopeFrameMap((positionalBindings + namedBindings).toMap())
        )
        return EvaluationResult.pure(Block(
            body = body,
            scope = scope
        ))
    }
}

internal data class ShapeTypeValue(
    val constantFields: Map<Identifier, InterpreterValue>
): Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        val constantFields = constantFields.toMap()
        val dynamicFields = namedArguments.toMap()
        return EvaluationResult.pure(ShapeValue(constantFields + dynamicFields))
    }

}

internal object UnionTypeValue: InterpreterValue()

internal data class ShapeValue(
    val fields: Map<Identifier, InterpreterValue>
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
            } else if (statement is Val && statement.expression is InterpreterValue) {
                return EvaluationResult.pure(Block(
                    body = body.drop(1),
                    scope = scope.add(statement.name, statement.expression)
                ))
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
            return EvaluationResult.pure(Block(elseBranch, scope = context.scope.enter()))
        } else {
            val branch = conditionalBranches[0]
            return when (branch.condition) {
                BooleanValue(false) ->
                    EvaluationResult.pure(If(
                        conditionalBranches = conditionalBranches.drop(1),
                        elseBranch = elseBranch
                    ))

                BooleanValue(true) ->
                    EvaluationResult.pure(Block(branch.body, scope = context.scope.enter()))

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

internal data class When(
    val expression: Expression,
    val expressionType: Type,
    val branches: List<WhenBranch>
): IncompleteExpression() {
    override fun evaluate(context: InterpreterContext): EvaluationResult<Expression> {
        return when (expression) {
            is IncompleteExpression ->
                expression.evaluate(context).map { evaluatedExpression ->
                    copy(expression = evaluatedExpression)
                }
            is ShapeValue -> {
                val branch = branches[0]
                // TODO: handle non-shape values in type
                val discriminator = findDiscriminator(sourceType = expressionType, targetType = branch.type as ShapeType)!!
                val discriminatorValue = expression.fields[discriminator.fieldName]!!
                if (discriminatorValue == symbolTypeToValue(discriminator.symbolType)) {
                    EvaluationResult.pure(Block(branch.body, context.scope.enter()))
                } else {
                    EvaluationResult.pure(copy(
                        branches = branches.drop(1)
                    ))
                }
            }
            else ->
                throw NotImplementedError()
        }
    }
}

internal data class WhenBranch(
    val type: Type,
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

internal abstract class Callable: InterpreterValue() {
    abstract fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression>
}

internal object IntToStringValue: Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        val argument = (positionalArguments[0] as IntegerValue).value
        return EvaluationResult.pure(StringValue(argument.toString()))
    }
}

internal object ListConstructorValue: Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        return EvaluationResult.pure(ListValue(positionalArguments))
    }
}

internal object PrintValue: Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        val argument = (positionalArguments[0] as StringValue).value
        return EvaluationResult.Value(UnitValue, stdout = argument)
    }
}

internal object PartialCallFunctionValue : Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        return EvaluationResult.pure(PartialCallValue(
            receiver = positionalArguments[0],
            positionalArguments = positionalArguments.drop(1),
            namedArguments = namedArguments
        ))
    }
}

internal data class PartialCallValue(
    val receiver: Expression,
    val positionalArguments: List<InterpreterValue>,
    val namedArguments: List<Pair<Identifier, InterpreterValue>>
): Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        return EvaluationResult.pure(Call(
            receiver = receiver,
            positionalArgumentExpressions = listOf(),
            positionalArgumentValues = this.positionalArguments + positionalArguments,
            namedArgumentExpressions = listOf(),
            namedArgumentValues = this.namedArguments + namedArguments
        ))
    }
}

internal class InterpreterContext(
    internal val scope: Scope,
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
    fun value(name: String, context: InterpreterContext): InterpreterValue {
        for (frame in frames) {
            val value = frame.value(name, context)
            if (value != null) {
                return value
            }
        }
        throw InterpreterError("Could not find variable: " + name)
    }

    fun add(name: Identifier, value: InterpreterValue): Scope {
        return Scope(listOf(frames[0].add(name, value)) + frames.drop(1))
    }

    fun enter(): Scope {
        return enter(ScopeFrameMap(mapOf()))
    }

    fun enter(frame: ScopeFrame): Scope {
        return Scope(frames = listOf(frame) + frames)
    }
}

internal interface ScopeFrame {
    fun value(name: String, context: InterpreterContext): InterpreterValue?
    fun add(name: Identifier, value: InterpreterValue): ScopeFrame

    companion object {
        val EMPTY = ScopeFrameMap(mapOf())
    }
}

internal data class ScopeFrameMap(private val variables: Map<String, InterpreterValue>): ScopeFrame {
    override fun value(name: String, context: InterpreterContext): InterpreterValue? {
        return variables[name]
    }

    override fun add(name: Identifier, value: InterpreterValue): ScopeFrame {
        if (variables.containsKey(name.value)) {
            throw InterpreterError("name is already bound in scope")
        } else {
            return ScopeFrameMap(mapOf(name.value to value) + variables)
        }
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

            val expression = moduleExpression.fieldExpressions.toMap()[identifier]
            if (expression != null) {
                throw InterpreterError("module field is not initialised: " + name)
            }

            return null
        }

        throw InterpreterError("Could not find module: " + moduleName)
    }

    override fun add(name: Identifier, value: InterpreterValue): ScopeFrame {
        throw UnsupportedOperationException("not implemented")
    }

}

internal val builtinStackFrame = ScopeFrameMap(mapOf(
    "intToString" to IntToStringValue,
    "list" to ListConstructorValue,
    "print" to PrintValue
))

internal fun fullyEvaluate(modules: ModuleSet, moduleName: List<Identifier>): ModuleEvaluationResult {
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
        moduleValues = mapOf()
    )
    val result = fullyEvaluate(call, context)
    val exitCode = when (result.value) {
        is IntegerValue -> result.value.value
        is UnitValue -> 0
        else -> throw NotImplementedError()
    }
    return ModuleEvaluationResult(exitCode = exitCode, stdout = result.stdout)
}

internal data class ModuleEvaluationResult(val exitCode: Int, val stdout: String)

internal fun fullyEvaluate(initialExpression: Expression, initialContext: InterpreterContext): EvaluationResult.Value<InterpreterValue> {
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
                    is EvaluationResult.ModuleValueUpdate -> {
                        context = context.updateModule(result.moduleName, result.value)
                    }
                    is EvaluationResult.ModuleExpressionUpdate -> {
                        context = context.updateModule(result.moduleName, result.value)
                    }
                }
            }
            is InterpreterValue ->
                return EvaluationResult.Value(value = expression, stdout = stdout.toString())
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

internal sealed class EvaluationResult<out T> {
    internal abstract fun <R> map(func: (T) -> R): EvaluationResult<R>

    internal data class Value<out T>(val value: T, val stdout: String): EvaluationResult<T>() {
        override fun <R> map(func: (T) -> R): EvaluationResult<R> {
            return EvaluationResult.Value(func(value), stdout)
        }
    }

    internal data class ModuleValueUpdate(
        val moduleName: List<Identifier>,
        val value: ModuleValue
    ): EvaluationResult<Nothing>() {
        override fun <R> map(func: (Nothing) -> R): EvaluationResult<R> {
            return this
        }
    }

    internal data class ModuleExpressionUpdate(
        val moduleName: List<Identifier>,
        val value: ModuleExpression
    ): EvaluationResult<Nothing>() {
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
    fun evaluate(moduleName: List<Identifier>, context: InterpreterContext): EvaluationResult<Nothing> {
        if (fieldExpressions.isEmpty()) {
            return EvaluationResult.ModuleValueUpdate(moduleName, ModuleValue(fieldValues.toMap()))
        } else {
            val (fieldName, expression) = fieldExpressions[0]
            when (expression) {
                is InterpreterValue ->
                    return EvaluationResult.ModuleExpressionUpdate(
                        moduleName,
                        ModuleExpression(
                            fieldExpressions = fieldExpressions.drop(1),
                            fieldValues = fieldValues + listOf(fieldName to expression)
                        )
                    )
                is IncompleteExpression -> {
                    val scope = Scope(
                        frames = moduleStackFrames(moduleName)
                    )
                    val result = expression.evaluate(context.inScope(scope))
                    when (result) {
                        is EvaluationResult.Value -> {
                            if (result.stdout != "") {
                                throw InterpreterError("Unexpected side effect")
                            }
                            return EvaluationResult.ModuleExpressionUpdate(
                                moduleName,
                                ModuleExpression(
                                    fieldExpressions = listOf(fieldName to result.value) + fieldExpressions.drop(1),
                                    fieldValues = fieldValues
                                )
                            )
                        }
                        is EvaluationResult.ModuleValueUpdate ->
                            return result
                        is EvaluationResult.ModuleExpressionUpdate ->
                            return result
                    }
                }

            }
        }
    }
}

internal fun symbolTypeToValue(symbolType: SymbolType): SymbolValue {
    return SymbolValue(
        moduleName = symbolType.module.map(::Identifier),
        name = symbolType.name
    )
}
