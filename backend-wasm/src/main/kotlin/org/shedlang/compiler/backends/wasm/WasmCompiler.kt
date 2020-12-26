package org.shedlang.compiler.backends.wasm

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.ModuleName
import org.shedlang.compiler.stackir.Image
import org.shedlang.compiler.stackir.Instruction
import org.shedlang.compiler.stackir.IrBool
import org.shedlang.compiler.stackir.PushValue
import java.lang.UnsupportedOperationException

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
            is PushValue -> {
                val value = instruction.value
                when (value) {
                    is IrBool -> {
                        val intValue = if (value.value) 1 else 0
                        return context.addInstruction(Wat.i32Const(intValue))
                    }
                    else -> {
                        throw UnsupportedOperationException("unhandled IR value: $value")
                    }
                }
            }
            else -> {
                throw UnsupportedOperationException("unhandled instruction: $instruction")
            }
        }
    }
}

internal class WasmFunctionContext(internal val instructions: PersistentList<SExpression>) {
    companion object {
        val INITIAL = WasmFunctionContext(instructions = persistentListOf())
    }

    fun addInstruction(instruction: SExpression): WasmFunctionContext {
        return WasmFunctionContext(
            instructions = instructions.add(instruction),
        )
    }
}
