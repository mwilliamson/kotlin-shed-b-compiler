package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.Operator
import org.shedlang.compiler.ast.Source
import org.shedlang.compiler.types.Effect
import org.shedlang.compiler.types.Type
import org.shedlang.compiler.types.TypeGroup
import org.shedlang.compiler.types.TypeParameter

/**
 * This indicates a bug in the compiler or its calling code
 */
open class CompilerError(message: String, val source: Source) : Exception(message)
class UnknownTypeError(val name: String, source: Source)
    : CompilerError("type of ${name} is unknown", source = source)

open class SourceError(message: String?, val source: Source): Exception(message)

open class TypeCheckError(message: String?, source: Source) : SourceError(message, source)
internal class BadStatementError(source: Source)
    : TypeCheckError("Bad statement", source)
class UnresolvedReferenceError(val name: String, source: Source)
    : TypeCheckError("Unresolved reference: " + name, source)
class UninitialisedVariableError(val name: String, source: Source)
    : TypeCheckError("Uninitialised variable: " + name, source)
class RedeclarationError(val name: String, source: Source)
    : TypeCheckError("Variable with name ${name} has already been declared", source)
class UnexpectedTypeError(val expected: TypeGroup, val actual: Type, source: Source)
    : TypeCheckError("Expected type ${expected.shortDescription} but was ${actual.shortDescription}", source)
class WrongNumberOfArgumentsError(val expected: Int, val actual: Int, source: Source)
    : TypeCheckError("Expected $expected arguments, but got $actual", source)
class WrongNumberOfStaticArgumentsError(val expected: Int, val actual: Int, source: Source)
    : TypeCheckError("Expected $expected static arguments, but got $actual", source)
class MissingArgumentError(val argumentName: String, source: Source)
    : TypeCheckError("Call is missing argument: $argumentName", source)
class ExtraArgumentError(val argumentName: String, source: Source)
    : TypeCheckError("Call has extra argument: $argumentName", source)
class ArgumentAlreadyPassedError(val argumentName: String, source: Source)
    : TypeCheckError("Argument has already been passed: $argumentName", source)
class PositionalArgumentPassedToShapeConstructorError(source: Source)
    : TypeCheckError("Positional arguments cannot be passed to shape constructors", source)
class CouldNotInferTypeParameterError(parameter: TypeParameter, source: Source)
    : TypeCheckError("Could not infer type for type parameter $parameter", source)
class NoSuchFieldError(val fieldName: String, source: Source)
    : TypeCheckError("No such field: " + fieldName, source)
class FieldAlreadyDeclaredError(val fieldName: String, source: Source)
    : TypeCheckError("Field has already been declared: ${fieldName}", source)
class UnhandledEffectError(val effect: Effect, source: Source)
    : TypeCheckError("Unhandled effect: ${effect}", source)
class InvalidOperationError(val operator: Operator, val operands: List<Type>, source: Source)
    : TypeCheckError(
    "Operation ${operator} is not valid for operands ${operands.map({operand -> operand.shortDescription}).joinToString(", ")}",
    source
)
class MissingReturnTypeError(message: String, source: Source)
    : TypeCheckError(message, source)
