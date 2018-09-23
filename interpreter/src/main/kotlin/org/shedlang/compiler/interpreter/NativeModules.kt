package org.shedlang.compiler.interpreter

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.Operator
import kotlin.math.min


private object ListsSequenceToListValue: Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        val sequence = positionalArguments[0] as ShapeValue
        return EvaluationResult.pure(call(
            ListsSequenceItemToListValue,
            positionalArgumentExpressions = listOf(
                call(
                    receiver = sequence.fields.getValue(Identifier("next"))
                )
            )
        ))
    }
}

private object ListsSequenceItemToListValue: Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        val item = positionalArguments[0] as ShapeValue
        // TODO: use proper type check
        val head = item.fields[Identifier("head")]
        if (head == null) {
            return EvaluationResult.pure(ListValue(listOf()))
        } else {
            val tail = item.fields.getValue(Identifier("tail")) as ShapeValue
            return EvaluationResult.pure(call(
                ListsConsValue,
                positionalArgumentExpressions = listOf(
                    head,
                    call(
                        ListsSequenceToListValue,
                        positionalArgumentValues = listOf(tail)
                    )
                )
            ))
        }
    }
}

private object ListsConsValue: Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        val head = positionalArguments[0]
        val tail = positionalArguments[1] as ListValue
        return EvaluationResult.pure(ListValue(listOf(head) + tail.elements))
    }

}

private object ListsListToSequenceValue: Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        val list = positionalArguments[0] as ListValue
        return EvaluationResult.pure(listIndexToSequence(list, 0))
    }

    private fun listIndexToSequence(list: ListValue, index: Int): Expression {
        val nextItem = object: Callable() {
            override fun call(
                positionalArguments: List<InterpreterValue>,
                namedArguments: List<Pair<Identifier, InterpreterValue>>
            ): EvaluationResult<Expression> {
                if (index < list.elements.size) {
                    return EvaluationResult.pure(call(
                        sequenceItemTypeReference,
                        namedArgumentExpressions = listOf(
                            Identifier("head") to list.elements[index],
                            Identifier("tail") to listIndexToSequence(list, index + 1)
                        )
                    ))
                } else {
                    return EvaluationResult.pure(sequenceEndReference)
                }
            }
        }

        return call(
            sequenceTypeReference,
            namedArgumentValues = listOf(
                Identifier("next") to nextItem
            )
        )
    }
}

private val listsModule = ModuleExpression(
    fieldExpressions = listOf(),
    fieldValues = listOf(
        Identifier("sequenceToList") to ListsSequenceToListValue,
        Identifier("listToSequence") to ListsListToSequenceValue
    )
)

private object StringsCharToHexStringValue: Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        val character = positionalArguments[0] as CharacterValue
        return EvaluationResult.pure(StringValue(character.value.toString(16).toUpperCase()))
    }
}

private object StringsCharToStringValue: Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        val character = positionalArguments[0] as CharacterValue
        val builder = StringBuilder()
        builder.appendCodePoint(character.value)
        return EvaluationResult.pure(StringValue(builder.toString()))
    }
}

private object StringsCodePointCountValue: Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        val string = positionalArguments[0] as StringValue
        val count = string.value.codePointCount(0, string.value.length)
        return EvaluationResult.pure(IntegerValue(count))
    }
}

private object StringsMapCharactersValue: Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        val func = positionalArguments[0]
        val string = positionalArguments[1] as StringValue
        if (string.value.isEmpty()) {
            return EvaluationResult.pure(StringValue(""))
        } else {
            return EvaluationResult.pure(BinaryOperation(
                Operator.ADD,
                call(func, positionalArgumentExpressions = listOf(CharacterValue(string.value.codePointAt(0)))),
                call(
                    StringsMapCharactersValue,
                    positionalArgumentValues = listOf(
                        func,
                        StringValue(string.value.substring(string.value.offsetByCodePoints(0, 1)))
                    )
                )
            ))
        }
    }
}

private object StringsRepeatValue: Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        val string = positionalArguments[0] as StringValue
        val times = positionalArguments[1] as IntegerValue
        return EvaluationResult.pure(StringValue(string.value.repeat(times.value.intValueExact())))
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

private object StringsSubstringValue: Callable() {
    override fun call(
        positionalArguments: List<InterpreterValue>,
        namedArguments: List<Pair<Identifier, InterpreterValue>>
    ): EvaluationResult<Expression> {
        val startIndexValue = positionalArguments[0] as IntegerValue
        val endIndexValue = positionalArguments[1] as IntegerValue
        val string = positionalArguments[2] as StringValue

        val startIndex = startIndexValue.value.toInt()
        val endIndex = endIndexValue.value.toInt()
        val result = string.value.substring(startIndex, min(endIndex, string.value.length))

        return EvaluationResult.pure(StringValue(result))
    }
}

private val stringsModule = ModuleExpression(
    fieldExpressions = listOf(),
    fieldValues = listOf(
        Identifier("charToHexString") to StringsCharToHexStringValue,
        Identifier("charToString") to StringsCharToStringValue,
        Identifier("codePointCount") to StringsCodePointCountValue,
        Identifier("mapCharacters") to StringsMapCharactersValue,
        Identifier("repeat") to StringsRepeatValue,
        Identifier("replace") to StringsReplaceValue,
        Identifier("substring") to StringsSubstringValue
    )
)

internal val nativeModules: Map<List<Identifier>, ModuleExpression> = mapOf(
    listOf(Identifier("stdlib"), Identifier("platform"), Identifier("Lists")) to listsModule,
    listOf(Identifier("stdlib"), Identifier("platform"), Identifier("Strings")) to stringsModule
)


private val optionsModuleReference = ModuleReference(listOf(Identifier("stdlib"), Identifier("Options")))
private val optionsNoneReference = FieldAccess(optionsModuleReference, Identifier("none"))
private val optionsSomeReference = FieldAccess(optionsModuleReference, Identifier("some"))

private val sequencesModuleReference = ModuleReference(listOf(Identifier("stdlib"), Identifier("Sequences")))
private val sequenceTypeReference = FieldAccess(sequencesModuleReference, Identifier("Sequence"))
private val sequenceItemTypeReference = FieldAccess(sequencesModuleReference, Identifier("SequenceItem"))
private val sequenceEndReference = FieldAccess(sequencesModuleReference, Identifier("end"))
