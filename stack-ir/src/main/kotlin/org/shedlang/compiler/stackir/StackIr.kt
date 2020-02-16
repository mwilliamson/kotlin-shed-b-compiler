package org.shedlang.compiler.stackir

import kotlinx.collections.immutable.PersistentList
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.ast.VariableBindingNode
import org.shedlang.compiler.backends.FieldInspector
import org.shedlang.compiler.types.Symbol
import org.shedlang.compiler.types.TagValue
import org.shedlang.compiler.types.Type
import java.math.BigInteger

sealed class IrValue

class IrBool(val value: Boolean): IrValue()

class IrCodePoint(val value: Int): IrValue()

class IrInt(val value: BigInteger): IrValue() {
    constructor(value: Int): this(value.toBigInteger())
}

class IrString(val value: String): IrValue()

class IrSymbol(val value: Symbol): IrValue()

class IrTagValue(val value: TagValue): IrValue()

object IrUnit: IrValue()

sealed class Instruction

object BoolEquals: Instruction()

object BoolNotEqual: Instruction()

object BoolNot: Instruction()

class Call(
    val positionalArgumentCount: Int,
    val namedArgumentNames: List<Identifier>
): Instruction()

class CallPartial(
    val positionalArgumentCount: Int,
    val namedArgumentNames: List<Identifier>
): Instruction()

object CodePointEquals: Instruction()

object CodePointNotEqual: Instruction()

object CodePointLessThan: Instruction()

object CodePointLessThanOrEqual: Instruction()

object CodePointGreaterThan: Instruction()

object CodePointGreaterThanOrEqual: Instruction()

class DeclareFunction(
    val name: String,
    val bodyInstructions: PersistentList<Instruction>,
    val positionalParameters: List<Parameter>,
    val namedParameters: List<Parameter>
): Instruction() {
    data class Parameter(val name: Identifier, val variableId: Int) {
        constructor(node: VariableBindingNode) : this(node.name, node.nodeId)
    }
}

class DeclareShape(
    val tagValue: TagValue?,
    val fields: List<FieldInspector>
): Instruction()

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

class Jump(val label: Int): Instruction()

class JumpIfFalse(val label: Int): Instruction()

class JumpIfTrue(val label: Int): Instruction()

class Label(val value: Int): Instruction()

class LocalLoad(val variableId: Int, val name: Identifier): Instruction() {
    constructor(node: VariableBindingNode) : this(node.nodeId, node.name)
    // TODO: extract more general notion of local (equivalent to DeclareFunction.Parameter)?
    constructor(parameter: DeclareFunction.Parameter) : this(parameter.variableId, parameter.name)
}

class LocalStore(val variableId: Int, val name: Identifier): Instruction() {
    constructor(node: VariableBindingNode) : this(node.nodeId, node.name)
}

class ModuleInit(val moduleName: ModuleName): Instruction()

class ModuleLoad(val moduleName: ModuleName): Instruction()

class ModuleStore(
    val moduleName: ModuleName,
    val exports: List<Pair<Identifier, Int>>
): Instruction()

class PushValue(val value: IrValue): Instruction()

object Return: Instruction()

object StringAdd: Instruction()

object StringEquals: Instruction()

object StringNotEqual: Instruction()

object Swap: Instruction()

object SymbolEquals: Instruction()

object TagValueAccess: Instruction()

object TagValueEquals: Instruction()

class TupleAccess(val elementIndex: Int): Instruction()

class TupleCreate(val length: Int): Instruction()

fun Instruction.children(): List<Instruction> {
    return when (this) {
        is DeclareFunction -> bodyInstructions
        else -> listOf()
    }
}

fun Instruction.descendants(): List<Instruction> {
    val children = children()
    return children + children.flatMap { child -> child.descendants() }
}

fun Instruction.descendantsAndSelf(): List<Instruction> {
    return listOf(this) + descendants()
}
