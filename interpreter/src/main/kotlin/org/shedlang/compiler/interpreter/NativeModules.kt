package org.shedlang.compiler.interpreter

import org.shedlang.compiler.ast.Identifier

private val listsModule = ModuleExpression(
    fieldExpressions = listOf(),
    fieldValues = listOf()
)

private object StringsCharToHexStringValue: Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        throw UnsupportedOperationException("not implemented")
    }
}

private object StringsCharToStringValue: Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        throw UnsupportedOperationException("not implemented")
    }
}

private object StringsCodePointCountValue: Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        throw UnsupportedOperationException("not implemented")
    }
}

private object StringsMapCharactersValue: Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        throw UnsupportedOperationException("not implemented")
    }
}

private object StringsReplaceValue: Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        throw UnsupportedOperationException("not implemented")
    }
}

private val stringsModule = ModuleExpression(
    fieldExpressions = listOf(),
    fieldValues = listOf(
        Identifier("charToHexString") to StringsCharToHexStringValue,
        Identifier("charToString") to StringsCharToStringValue,
        Identifier("codePointCount") to StringsCodePointCountValue,
        Identifier("mapCharacters") to StringsMapCharactersValue,
        Identifier("replace") to StringsReplaceValue
    )
)

private object TypesCastValue: Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        throw UnsupportedOperationException("not implemented")
    }
}

private object TypesNameValue: Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        throw UnsupportedOperationException("not implemented")
    }
}

private object TypesTypeOfValue: Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        throw UnsupportedOperationException("not implemented")
    }
}

private val typesModule = ModuleExpression(
    fieldExpressions = listOf(),
    fieldValues = listOf(
        Identifier("cast") to TypesCastValue,
        Identifier("name") to TypesNameValue,
        Identifier("typeOf") to TypesTypeOfValue
    )
)

internal val nativeModules: Map<List<Identifier>, ModuleExpression> = mapOf(
    listOf(Identifier("stdlib"), Identifier("platform"), Identifier("Lists")) to listsModule,
    listOf(Identifier("stdlib"), Identifier("platform"), Identifier("Strings")) to stringsModule,
    listOf(Identifier("stdlib"), Identifier("platform"), Identifier("Types")) to typesModule
)
