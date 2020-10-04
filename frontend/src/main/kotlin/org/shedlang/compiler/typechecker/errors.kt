package org.shedlang.compiler.typechecker

import org.shedlang.compiler.CompilerError
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.types.*

/**
 * This indicates a bug in the compiler or its calling code
 */
class UnknownTypeError(val name: Identifier, source: Source)
    : CompilerError("type of ${name.value} is unknown", source = source)

open class SourceError(message: String?, val source: Source): Exception(message)

open class TypeCheckError(message: String?, source: Source) : SourceError(message, source)
internal class BadStatementError(source: Source)
    : TypeCheckError("Bad statement", source)
class UnresolvedReferenceError(val name: Identifier, source: Source)
    : TypeCheckError("Unresolved reference: " + name.value, source)
class UninitialisedVariableError(val name: Identifier, source: Source)
    : TypeCheckError("Uninitialised variable: " + name.value, source)
class RedeclarationError(val name: Identifier, source: Source)
    : TypeCheckError("Variable with name ${name.value} has already been declared", source)
class UnexpectedTypeError(val expected: TypeGroup, val actual: Type, source: Source)
    : TypeCheckError("Expected type ${expected.shortDescription} but was ${actual.shortDescription}", source)
class WrongNumberOfArgumentsError(val expected: Int, val actual: Int, source: Source)
    : TypeCheckError("Expected $expected arguments, but got $actual", source)
class WrongNumberOfStaticArgumentsError(val expected: Int, val actual: Int, source: Source)
    : TypeCheckError("Expected $expected static arguments, but got $actual", source)
class MissingArgumentError(val argumentName: Identifier, source: Source)
    : TypeCheckError("Call is missing argument: ${argumentName.value}", source)
class ExtraArgumentError(val argumentName: Identifier, source: Source)
    : TypeCheckError("Call has extra argument: ${argumentName.value}", source)
class ArgumentAlreadyPassedError(val argumentName: Identifier, source: Source)
    : TypeCheckError("Argument has already been passed: ${argumentName.value}", source)
class PositionalArgumentPassedToShapeConstructorError(source: Source)
    : TypeCheckError("Positional arguments cannot be passed to shape constructors", source)
class CouldNotInferTypeParameterError(parameter: TypeParameter, source: Source)
    : TypeCheckError("Could not infer type for type parameter ${parameter.shortDescription}, from:\n${parameter.source.describe()}", source)
class NoSuchFieldError(val fieldName: Identifier, source: Source)
    : TypeCheckError("No such field: " + fieldName.value, source)
class FieldAlreadyDeclaredError(val fieldName: Identifier, source: Source)
    : TypeCheckError("Field has already been declared: ${fieldName.value}", source)
class CallWithEffectMissingEffectFlag(source: Source)
    : TypeCheckError("Calls with effects must use !", source)
class UnhandledEffectError(val effect: Effect, source: Source)
    : TypeCheckError("Unhandled effect: ${effect.shortDescription}", source)
class ReceiverHasNoEffectsError(source: Source)
    : TypeCheckError("Receiver has no effects", source)
class InvalidBinaryOperationError(val operator: BinaryOperator, val left: Type, val right: Type, source: Source)
    : TypeCheckError(
    "Binary operation ${operator} is not valid for operands ${left.shortDescription}, ${right.shortDescription}",
    source
)
class InvalidUnaryOperationError(val operator: UnaryOperator, val actualOperandType: Type, source: Source)
    : TypeCheckError(
    "Unary operation ${operator} is not valid for operand ${actualOperandType.shortDescription}",
    source
)
class FieldDeclarationShapeIdConflictError(val name: Identifier, source: Source)
    : TypeCheckError("A field with the name ${name} from a different shape has already been declared", source)
class FieldDeclarationMergeTypeConflictError(
    val name: Identifier,
    val types: List<Type>,
    source: Source
): TypeCheckError("A field with the name ${name} is declared with incompatible types " +
    types.joinToString(",") { type -> type.shortDescription }, source)
class FieldDeclarationOverrideTypeConflictError(
    val name: Identifier,
    val overrideType: Type,
    val parentShape: Identifier,
    val parentType: Type,
    source: Source
): TypeCheckError("The field ${name} is declared with the type ${overrideType.shortDescription}," +
    " but the parent ${parentShape} declares ${name} with the type ${parentType.shortDescription}", source)
class FieldDeclarationValueConflictError(
    val name: Identifier,
    val parentShape: Identifier,
    source: Source
): TypeCheckError("The field ${name} is declared with a constant value in parent ${parentShape}," +
    " and constant fields cannot be overridden", source)
class MissingReturnTypeError(message: String, source: Source)
    : TypeCheckError(message, source)
class WhenIsNotExhaustiveError(val unhandledMembers: List<Type>, source: Source)
    : TypeCheckError(
        "when is not exhaustive, unhandled members: " + unhandledMembers.joinToString(
            ", ",
            transform = { member -> member.shortDescription }
        ),
        source = source
    )
class WhenElseIsNotReachableError(source: Source) : TypeCheckError(
    "else branch of when is not reachable",
    source = source
)
class ExpectedUserDefinedEffectError(source: Source): TypeCheckError(
    "expected user-defined effect",
    source = source
)
class MissingHandlerError(val name: Identifier, source: Source) : TypeCheckError(
    "missing handler for ${name.value}",
    source = source
)
class UnknownOperationError(val effect: Effect, val operationName: Identifier, source: Source) : TypeCheckError(
    "effect ${effect.shortDescription} has no operation ${operationName.value}",
    source = source
)
class CannotResumeWithStateInStatelessHandleError(source: Source): TypeCheckError(
    "cannot resume with state in stateless handle",
    source = source
)
class ResumeMissingNewStateError(source: Source): TypeCheckError(
    "resume is missing new state in stateful handle",
    source = source,
)
class CannotResumeOutsideOfHandler(source: Source): TypeCheckError(
    "cannot resume outside of handler",
    source = source,
)
class ModuleNotFoundError(val name: ModuleName, source: Source)
    : TypeCheckError("Module not found: " + name.map(Identifier::value).joinToString("."), source = source)
class MultipleModulesWithSameNameFoundError(val name: ModuleName, source: Source)
    : TypeCheckError("More than one module with the name ${name.map(Identifier::value).joinToString(".")} was found", source = source)
class CouldNotFindDiscriminator(val sourceType: Type, val targetType: StaticValue, source: Source)
    : TypeCheckError("Could not find discriminator from ${sourceType.shortDescription} to ${targetType.shortDescription}", source = source)

class InvalidTailCall(source: Source): TypeCheckError("tail calls must be self-recursive calls", source = source)
