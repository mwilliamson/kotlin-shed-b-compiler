package org.shedlang.compiler.stackir

import kotlinx.collections.immutable.PersistentList
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.backends.FieldInspector
import org.shedlang.compiler.types.Symbol
import org.shedlang.compiler.types.TagValue
import org.shedlang.compiler.types.Type
import java.math.BigInteger

sealed class IrValue

class IrBool(val value: Boolean): IrValue()

class IrCodePoint(val value: Int): IrValue()

class IrInt(val value: BigInteger): IrValue()

class IrString(val value: String): IrValue()

class IrSymbol(val value: Symbol): IrValue()

object IrUnit: IrValue()

data class NamedParameterId(val name: Identifier, val variableId: Int)

sealed class Instruction

object BoolEquals: Instruction()

object BoolNotEqual: Instruction()

object BoolNot: Instruction()

class Call(
    val positionalArgumentCount: Int,
    val namedArgumentNames: List<Identifier>
): Instruction()

class PartialCall(
    val positionalArgumentCount: Int,
    val namedArgumentNames: List<Identifier>
): Instruction()

object CodePointEquals: Instruction()

object CodePointNotEqual: Instruction()

object CodePointLessThan: Instruction()

object CodePointLessThanOrEqual: Instruction()

object CodePointGreaterThan: Instruction()

object CodePointGreaterThanOrEqual: Instruction()

class CreateTuple(val length: Int): Instruction()

class DeclareFunction(
    val bodyInstructions: PersistentList<Instruction>,
    val positionalParameterIds: List<Int>,
    val namedParameterIds: List<NamedParameterId>
): Instruction()

class DeclareShape(
    val tagValue: TagValue?,
    val fields: List<FieldInspector>
): Instruction()

object DeclareVarargs: Instruction()

object Discard: Instruction()

object Duplicate: Instruction()

object Exit: Instruction()

// TODO: require Type
class FieldAccess(val fieldName: Identifier, val receiverType: Type?): Instruction()

object IntAdd: Instruction()

object IntEquals: Instruction()

object IntMinus: Instruction()

object IntMultiply: Instruction()

object IntNotEqual: Instruction()

object IntSubtract: Instruction()

class LocalLoad(val variableId: Int): Instruction()

class LocalStore(val variableId: Int): Instruction()

class ModuleInit(val moduleName: List<Identifier>): Instruction()

class ModuleLoad(val moduleName: List<Identifier>): Instruction()

class ModuleStore(
    val moduleName: List<Identifier>,
    val exports: List<Pair<Identifier, Int>>
): Instruction()

class PushValue(val value: IrValue): Instruction()

class RelativeJump(val size: Int): Instruction()

class RelativeJumpIfFalse(val size: Int): Instruction()

class RelativeJumpIfTrue(val size: Int): Instruction()

object Return: Instruction()

object StringAdd: Instruction()

object StringEquals: Instruction()

object StringNotEqual: Instruction()

object Swap: Instruction()

object SymbolEquals: Instruction()

object TagValueAccess: Instruction()

class TupleAccess(val elementIndex: Int): Instruction()
