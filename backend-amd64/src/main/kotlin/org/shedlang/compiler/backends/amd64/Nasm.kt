package org.shedlang.compiler.backends.amd64

interface Operand {
    fun serialise(): String
}

class RegisterOperand(private val name: String): Operand {
    override fun serialise(): String {
        return name
    }
}

object Registers {
    val rax = RegisterOperand("rax")
    val rdx = RegisterOperand("rdx")
    val rbp = RegisterOperand("rbp")
    val rsp = RegisterOperand("rsp")
}

enum class OperandSize {
    IMPLICIT,
    DWORD,
    QWORD
}

private class ImmediateDwordOperand(private val value: Int): Operand {
    override fun serialise(): String {
        return "dword $value"
    }
}

private class ImmediateQwordOperand(private val value: Long): Operand {
    override fun serialise(): String {
        return "qword $value"
    }
}

class ImmediateOperand(private val value: Long): Operand {
    override fun serialise(): String {
        return value.toString()
    }
}

object Immediates {
    fun dword(value: Int): Operand = ImmediateDwordOperand(value)
    fun qword(value: Long): Operand = ImmediateQwordOperand(value)
    fun int(value: Int): Operand = ImmediateOperand(value.toLong())
}

class MemoryOperand(
    private val base: Operand,
    private val offset: Int = 0,
    private val operandSize: OperandSize = OperandSize.IMPLICIT
): Operand {
    override fun serialise(): String {
        val sizeString = when (operandSize) {
            OperandSize.IMPLICIT -> ""
            OperandSize.DWORD -> "dword "
            OperandSize.QWORD -> "qword "
        }
        val offsetString = if (offset == 0) "" else " + $offset"
        return "$sizeString[${base.serialise()}$offsetString]"
    }
}

class LabelOperand(private val name: String): Operand {
    override fun serialise(): String {
        return name
    }
}

interface Line {
    fun serialise(): String
}

class Label(private val name: String): Line {
    override fun serialise(): String {
        return "$name:"
    }
}

class Directive(private val string: String): Line {
    override fun serialise(): String {
        return "    $string"
    }
}

object Directives {
    fun global(name: String): Directive {
        return Directive("global $name")
    }

    val defaultRel = Directive("default rel")

    fun section(name: String): Directive {
        return Directive("section $name")
    }

    val bssSection = section(".bss")
    val textSection = section(".text")
}

class Instruction(private val name: String, private val operands: List<Operand>): Line {
    override fun serialise(): String {
        return "    $name ${operands.joinToString(", ") { operand -> operand.serialise() }}"
    }
}

object Instructions {
    fun call(label: String): Instruction {
        return call(LabelOperand(label))
    }

    fun call(operand: Operand): Instruction {
        return Instruction("call", listOf(operand))
    }

    fun cmp(left: Operand, right: Operand): Instruction {
        return Instruction("cmp", listOf(left, right))
    }

    fun jne(target: Operand): Instruction {
        return Instruction("jne", listOf(target))
    }

    fun lea(destination: Operand, source: MemoryOperand): Instruction {
        return Instruction("lea", listOf(destination, source))
    }

    fun mov(destination: RegisterOperand, source: Int): Instruction {
        return mov(destination, source)
    }

    fun mov(destination: Operand, source: Operand): Instruction {
        return Instruction("mov", listOf(destination, source))
    }

    fun pop(operand: Operand): Instruction {
        return Instruction("pop", listOf(operand))
    }

    fun push(operand: Operand): Instruction {
        return Instruction("push", listOf(operand))
    }

    fun resq(size: Int): Instruction {
        return Instruction("resq", listOf(ImmediateOperand(size.toLong())))
    }

    val ret = Instruction("ret", listOf())

    fun sub(destination: Operand, source: Operand): Instruction {
        return Instruction("sub", listOf(destination, source))
    }
}

fun serialise(lines: List<Line>): String {
    return lines.joinToString("") { line -> "${line.serialise()}\n" }
}
