package org.shedlang.compiler.interpreter

import org.shedlang.compiler.ast.BinaryOperator
import org.shedlang.compiler.ast.Identifier
import kotlin.math.min


private object ListsSequenceToListValue: Callable() {
    override fun call(arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression> {
        val sequence = arguments[0] as ShapeValue
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
    override fun call(arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression> {
        val item = arguments[0] as ShapeValue
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
    override fun call(arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression> {
        val head = arguments[0]
        val tail = arguments[1] as ListValue
        return EvaluationResult.pure(ListValue(listOf(head) + tail.elements))
    }

}

private object ListsListToSequenceValue: Callable() {
    override fun call(arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression> {
        val list = arguments[0] as ListValue
        return EvaluationResult.pure(listIndexToSequence(list, 0))
    }

    private fun listIndexToSequence(list: ListValue, index: Int): Expression {
        val nextItem = object: Callable() {
            override fun call(arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression> {
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

private object StringsCodePointToHexStringValue: Callable() {
    override fun call(arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression> {
        val character = arguments[0] as CodePointValue
        return EvaluationResult.pure(StringValue(character.value.toString(16).toUpperCase()))
    }
}

private object StringsCodePointToIntValue: Callable() {
    override fun call(arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression> {
        val character = arguments[0] as CodePointValue
        return EvaluationResult.pure(IntegerValue(character.value))
    }
}

private object StringsCodePointToStringValue: Callable() {
    override fun call(arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression> {
        val character = arguments[0] as CodePointValue
        val builder = StringBuilder()
        builder.appendCodePoint(character.value)
        return EvaluationResult.pure(StringValue(builder.toString()))
    }
}

private object StringsCodePointCountValue: Callable() {
    override fun call(arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression> {
        val string = arguments[0].string()
        val count = string.codePointCount(0, string.length)
        return EvaluationResult.pure(IntegerValue(count))
    }
}

private object StringsFirstCodePointValue: Callable() {
    override fun call(arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression> {
        val string = arguments[0].string()
        val value = if (string.isEmpty()) {
            optionsNoneReference
        } else {
            Call(
                receiver = optionsSomeReference,
                positionalArgumentExpressions = listOf(),
                namedArgumentExpressions = listOf(),
                positionalArgumentValues = listOf(CodePointValue(string.codePointAt(0))),
                namedArgumentValues = listOf()
            )
        }
        return EvaluationResult.pure(value)
    }
}

private object StringsFlatMapCodePointsValue: Callable() {
    override fun call(arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression> {
        val func = arguments[0]
        val string = arguments[1].string()
        if (string.isEmpty()) {
            return EvaluationResult.pure(StringValue(""))
        } else {
            return EvaluationResult.pure(BinaryOperation(
                BinaryOperator.ADD,
                call(func, positionalArgumentExpressions = listOf(CodePointValue(string.codePointAt(0)))),
                call(
                    StringsFlatMapCodePointsValue,
                    positionalArgumentValues = listOf(
                        func,
                        StringValue(string.substring(string.offsetByCodePoints(0, 1)))
                    )
                )
            ))
        }
    }
}

private object StringsFoldLeftCodePointsValue: Callable() {
    override fun call(arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression> {
        val func = arguments[0]
        val initial = arguments[1]
        val string = arguments[2].string()
        if (string.isEmpty()) {
            return EvaluationResult.pure(initial)
        } else {
            return EvaluationResult.pure(call(
                StringsFoldLeftCodePointsValue,
                positionalArgumentExpressions = listOf(
                    func,
                    call(func, positionalArgumentExpressions = listOf(initial, CodePointValue(string.codePointAt(0)))),
                    StringValue(string.substring(string.offsetByCodePoints(0, 1)))
                )
            ))
        }
    }
}

private object StringsRepeatValue: Callable() {
    override fun call(arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression> {
        val string = arguments[0].string()
        val times = arguments[1].int()
        return EvaluationResult.pure(StringValue(string.repeat(times.intValueExact())))
    }
}

private object StringsReplaceValue: Callable() {
    override fun call(arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression> {
        throw UnsupportedOperationException("not implemented")
    }
}

private object StringsSubstringValue: Callable() {
    override fun call(arguments: Arguments, context: InterpreterContext): EvaluationResult<Expression> {
        val startIndexValue = arguments[0].int()
        val endIndexValue = arguments[1].int()
        val string = arguments[2].string()

        val startIndex = startIndexValue.intValueExact()
        val endIndex = endIndexValue.intValueExact()
        val result = string.substring(startIndex, min(endIndex, string.length))

        return EvaluationResult.pure(StringValue(result))
    }
}

private val stringsModule = ModuleExpression(
    fieldExpressions = listOf(),
    fieldValues = listOf(
        Identifier("codePointToHexString") to StringsCodePointToHexStringValue,
        Identifier("codePointToInt") to StringsCodePointToIntValue,
        Identifier("codePointToString") to StringsCodePointToStringValue,
        Identifier("codePointCount") to StringsCodePointCountValue,
        Identifier("firstCodePoint") to StringsFirstCodePointValue,
        Identifier("flatMapCodePoints") to StringsFlatMapCodePointsValue,
        Identifier("foldLeftCodePoints") to StringsFoldLeftCodePointsValue,
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
