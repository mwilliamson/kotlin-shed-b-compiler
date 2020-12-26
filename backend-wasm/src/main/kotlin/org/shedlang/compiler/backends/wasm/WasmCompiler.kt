package org.shedlang.compiler.backends.wasm

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.stackir.*
import java.lang.UnsupportedOperationException

// TODO: Int implementation should be big integers, not i32
internal class WasmCompiler(private val image: Image, private val moduleSet: ModuleSet) {
    class CompilationResult(val wat: String)

    fun compile(mainModule: ModuleName): CompilationResult {
        val messageOffset = 8
        val message = "Hello, world!\n"
        val wat = Wat.module(
            imports = listOf(
                Wasi.importFdWrite("fd_write"),
            ),
            body = listOf(
                Wat.data(offset = messageOffset, value = message),
                Wat.func(
                    identifier = "start",
                    body = listOf(
                        Wat.I.i32Store(Wat.i32Const(0), Wat.i32Const(messageOffset)),
                        Wat.I.i32Store(Wat.i32Const(4), Wat.i32Const(message.length)),
                    ),
                ),
                Wat.start("start"),
                Wat.func(
                    identifier = "main",
                    exportName = "_start",
                    body = listOf(
                        Wasi.callFdWrite(
                            identifier = "fd_write",
                            fileDescriptor = Wasi.stdout,
                            iovs = Wat.i32Const(0),
                            iovsLen = Wat.i32Const(1),
                            nwritten = Wat.i32Const(8 + message.length),
                        ),
                        Wat.I.drop,
                    ),
                ),
            ),
        ).serialise()
        return CompilationResult(wat = wat)
    }

    internal fun compileInstructions(instructions: List<Instruction>, context: WasmFunctionContext): WasmFunctionContext {
        return instructions.fold(context, { currentContext, instruction -> compileInstruction(instruction, currentContext) })
    }

    private fun compileInstruction(instruction: Instruction, context: WasmFunctionContext): WasmFunctionContext {
        when (instruction) {
            is BoolEquals -> {
                return context.addInstruction(Wat.I.i32Eq)
            }

            is BoolNotEqual -> {
                return context.addInstruction(Wat.I.i32Ne)
            }

            is BoolNot -> {
                val (context2, local) = context.addLocal()

                return context2
                    .addInstruction(Wat.I.localSet(local))
                    .addInstruction(Wat.i32Const(1))
                    .addInstruction(Wat.I.localGet(local))
                    .addInstruction(Wat.I.i32Sub)
            }

            is Discard -> {
                return context.addInstruction(Wat.I.drop)
            }

            is IntAdd -> {
                return context.addInstruction(Wat.I.i32Add)
            }

            is IntEquals -> {
                return context.addInstruction(Wat.I.i32Eq)
            }

            is IntGreaterThan -> {
                return context.addInstruction(Wat.I.i32GtS)
            }

            is IntGreaterThanOrEqual -> {
                return context.addInstruction(Wat.I.i32GeS)
            }

            is IntLessThan -> {
                return context.addInstruction(Wat.I.i32LtS)
            }

            is IntLessThanOrEqual -> {
                return context.addInstruction(Wat.I.i32LeS)
            }

            is IntMinus -> {
                val (context2, local) = context.addLocal()

                return context2
                    .addInstruction(Wat.I.localSet(local))
                    .addInstruction(Wat.i32Const(0))
                    .addInstruction(Wat.I.localGet(local))
                    .addInstruction(Wat.I.i32Sub)
            }

            is IntMultiply -> {
                return context.addInstruction(Wat.I.i32Mul)
            }

            is IntNotEqual -> {
                return context.addInstruction(Wat.I.i32Ne)
            }

            is IntSubtract -> {
                return context.addInstruction(Wat.I.i32Sub)
            }

            is LocalLoad -> {
                val (context2, identifier) = context.variableToLocal(
                    variableId = instruction.variableId,
                    name = instruction.name,
                )
                return context2.addInstruction(Wat.I.localGet(identifier))
            }

            is LocalStore -> {
                val (context2, identifier) = context.variableToLocal(
                    variableId = instruction.variableId,
                    name = instruction.name,
                )
                return context2.addInstruction(Wat.I.localSet(identifier))
            }

            is PushValue -> {
                val value = instruction.value
                when (value) {
                    is IrBool -> {
                        val intValue = if (value.value) 1 else 0
                        return context.addInstruction(Wat.i32Const(intValue))
                    }
                    is IrInt -> {
                        return context.addInstruction(Wat.i32Const(value.value.intValueExact()))
                    }
                    is IrUnicodeScalar -> {
                        return context.addInstruction(Wat.i32Const(value.value))
                    }
                    is IrUnit -> {
                        return context.addInstruction(Wat.i32Const(0))
                    }
                    else -> {
                        throw UnsupportedOperationException("unhandled IR value: $value")
                    }
                }
            }

            is UnicodeScalarEquals -> {
                return context.addInstruction(Wat.I.i32Eq)
            }

            is UnicodeScalarGreaterThan -> {
                return context.addInstruction(Wat.I.i32GtU)
            }

            is UnicodeScalarGreaterThanOrEqual -> {
                return context.addInstruction(Wat.I.i32GeU)
            }

            is UnicodeScalarLessThan -> {
                return context.addInstruction(Wat.I.i32LtU)
            }

            is UnicodeScalarLessThanOrEqual -> {
                return context.addInstruction(Wat.I.i32LeU)
            }

            is UnicodeScalarNotEqual -> {
                return context.addInstruction(Wat.I.i32Ne)
            }

            else -> {
                throw UnsupportedOperationException("unhandled instruction: $instruction")
            }
        }
    }
}

private const val initialLocalIndex = 1

internal data class WasmFunctionContext(
    internal val instructions: PersistentList<SExpression>,
    private val nextLocalIndex: Int,
    internal val locals: PersistentList<String>,
    private val variableIdToLocal: PersistentMap<Int, String>,
) {
    companion object {
        val INITIAL = WasmFunctionContext(
            instructions = persistentListOf(),
            nextLocalIndex = initialLocalIndex,
            locals = persistentListOf(),
            variableIdToLocal = persistentMapOf(),
        )
    }

    fun addInstruction(instruction: SExpression): WasmFunctionContext {
        return copy(
            instructions = instructions.add(instruction),
        )
    }

    fun addLocal(name: String = "temp"): Pair<WasmFunctionContext, String> {
        val local = "local_${name}_${nextLocalIndex}"
        val newContext = copy(locals = locals.add(local), nextLocalIndex = nextLocalIndex + 1)
        return Pair(newContext, local)
    }

    fun variableToLocal(variableId: Int, name: Identifier): Pair<WasmFunctionContext, String> {
        val existingLocal = variableIdToLocal[variableId]
        if (existingLocal == null) {
            val (context2, local) = addLocal(name.value)
            val newContext = context2.copy(variableIdToLocal = variableIdToLocal.put(variableId, local))
            return Pair(newContext, local)
        } else {
            return Pair(this, existingLocal)
        }
    }
}
