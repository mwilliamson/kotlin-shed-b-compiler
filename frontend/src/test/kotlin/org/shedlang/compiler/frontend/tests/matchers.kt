package org.shedlang.compiler.frontend.tests

import com.natpryce.hamkrest.*
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.tests.allOf
import org.shedlang.compiler.tests.anythingOrNull
import org.shedlang.compiler.tests.isMap
import org.shedlang.compiler.tests.isSequence
import org.shedlang.compiler.types.*

internal fun isIdentifier(name: String) = has(Identifier::value, equalTo(name))

internal fun isType(type: Type): Matcher<Type> = equalTo(type)

internal fun isFunctionType(
    positionalParameters: Matcher<List<Type>> = anything,
    returnType: Matcher<Type> = anything,
    namedParameters: Matcher<Map<Identifier, Type>> = anything,
    effect: Matcher<Effect> = anything
): Matcher<Type> = cast(allOf(
    has(FunctionType::positionalParameters, positionalParameters),
    has(FunctionType::namedParameters, namedParameters),
    has(FunctionType::returns, returnType),
    has(FunctionType::effect, effect)
))

internal fun isShapeType(
    name: Matcher<Identifier> = anything,
    staticArguments: Matcher<List<StaticValue>> = anything,
    shapeId: Matcher<Int> = anything
): Matcher<StaticValue> = cast(allOf(
    has(ShapeType::name, name),
    has(ShapeType::staticArguments, staticArguments),
    has(ShapeType::shapeId, shapeId)
))

internal fun isShapeType(
    name: Matcher<Identifier> = anything,
    staticArguments: Matcher<List<StaticValue>> = anything,
    fields: List<Pair<String, Matcher<Field>>>
): Matcher<StaticValue> = cast(allOf(
    has(ShapeType::name, name),
    has(ShapeType::staticArguments, staticArguments),
    has(ShapeType::fields, isMap(*fields.map { (name, type) -> Identifier(name) to type }.toTypedArray()))
))

internal fun isField(type: Matcher<Type>) = has(Field::type, type)

internal fun isUnionType(
    name: Matcher<Identifier> = anything,
    staticArguments: Matcher<List<StaticValue>> = anything,
    members: Matcher<List<Type>> = anything
): Matcher<StaticValue> = cast(allOf(
    has(UnionType::name, name),
    has(UnionType::staticArguments, staticArguments),
    has(UnionType::members, members)
))

internal val isAnyType: Matcher<StaticValue> = cast(equalTo(AnyType))
internal val isNothingType: Matcher<StaticValue> = cast(equalTo(NothingType))
internal val isUnitType: Matcher<StaticValue> = cast(equalTo(UnitType))
internal val isIntType: Matcher<StaticValue> = cast(equalTo(IntType))
internal val isBoolType: Matcher<StaticValue> = cast(equalTo(BoolType))
internal val isStringType: Matcher<StaticValue> = cast(equalTo(StringType))

internal fun isMetaType(type: Matcher<Type>): Matcher<StaticValue> = isShapeType(
    shapeId = equalTo(metaTypeShapeId),
    staticArguments = isSequence(cast(type))
)

internal fun isEffectType(effect: Matcher<Effect>): Matcher<StaticValue> = cast(has(EffectType::effect, effect))

internal fun isListType(elementType: Matcher<StaticValue>): Matcher<StaticValue> = isShapeType(
    shapeId = equalTo(listTypeShapeId),
    staticArguments = isSequence(elementType)
)

internal fun isTypeFunction(
    parameters: Matcher<List<StaticParameter>>,
    type: Matcher<Type> = anything
): Matcher<Type> = cast(allOf(
    has(TypeFunction::parameters, parameters),
    has(TypeFunction::type, type)
))

internal fun isTypeParameter(
    name: Matcher<Identifier> = anything,
    variance: Matcher<Variance> = anything,
    memberOf: Matcher<Type?> = anythingOrNull
): Matcher<StaticValue> = cast(allOf(
    has(TypeParameter::name, name),
    has(TypeParameter::variance, variance)
))

internal val isInvariant = equalTo(Variance.INVARIANT)
internal val isCovariant = equalTo(Variance.COVARIANT)
internal val isContravariant = equalTo(Variance.CONTRAVARIANT)

internal fun isEquivalentType(type: Type): Matcher<Type> {
    return object: Matcher.Primitive<Type>() {
        override fun invoke(actual: Type): MatchResult {
            if (org.shedlang.compiler.typechecker.isEquivalentType(type, actual)) {
                return MatchResult.Match
            } else {
                return MatchResult.Mismatch("was: " + actual)
            }
        }

        override val description: String
            get() = "is equivalent to " + type

    }
}

internal val isUnionTypeGroup: Matcher<TypeGroup> = equalTo(UnionTypeGroup)
internal val isMetaTypeGroup: Matcher<TypeGroup> = equalTo(MetaTypeGroup)

inline fun <reified T : Throwable> throwsException(exceptionCriteria: Matcher<T>? = null): Matcher<() -> Unit> {
    val exceptionName = T::class.qualifiedName

    return object : Matcher<() -> Unit> {
        override fun invoke(actual: () -> Unit): MatchResult =
            try {
                actual()
                MatchResult.Mismatch("did not throw")
            } catch (e: Throwable) {
                if (e is T) {
                    exceptionCriteria?.invoke(e) ?: MatchResult.Match
                } else {
                    throw e
                }
            }

        override val description: String get() = "throws ${exceptionName}${exceptionCriteria?.let { " that ${describe(it)}" } ?: ""}"
        override val negatedDescription: String get() = "does not throw ${exceptionName}${exceptionCriteria?.let { " that ${describe(it)}" } ?: ""}"
    }
}
