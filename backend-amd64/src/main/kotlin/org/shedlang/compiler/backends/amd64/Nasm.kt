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
}

class ConstantOperand(private val value: Int): Operand {
    override fun serialise(): String {
        return value.toString()
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
}

class Instruction(private val name: String, private val operands: List<Operand>): Line {
    override fun serialise(): String {
        return "    $name ${operands.joinToString(", ") { operand -> operand.serialise() }}"
    }
}

object Instructions {
    fun mov(destination: RegisterOperand, source: Int): Instruction {
        return Instruction("mov", listOf(destination, ConstantOperand(source)))
    }

    fun call(label: String): Instruction {
        return Instruction("call", listOf(LabelOperand(label)))
    }

    val ret = Instruction("ret", listOf())
}

fun serialise(lines: List<Line>): String {
    return lines.joinToString("") { line -> "${line.serialise()}\n" }
}
