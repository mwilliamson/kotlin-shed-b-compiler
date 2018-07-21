package org.shedlang.compiler.interpreter

import org.shedlang.compiler.ast.*

interface InterpreterValue {

}

object UnitValue: InterpreterValue
data class BooleanValue(val value: Boolean): InterpreterValue
data class IntegerValue(val value: Int): InterpreterValue
data class StringValue(val value: String): InterpreterValue
data class CharacterValue(val value: Int): InterpreterValue
data class SymbolValue(val name: String): InterpreterValue

fun evaluate(expression: ExpressionNode): InterpreterValue {
    return expression.accept(object : ExpressionNode.Visitor<InterpreterValue> {
        override fun visit(node: UnitLiteralNode) = UnitValue
        override fun visit(node: BooleanLiteralNode) = BooleanValue(node.value)
        override fun visit(node: IntegerLiteralNode) = IntegerValue(node.value)
        override fun visit(node: StringLiteralNode) = StringValue(node.value)
        override fun visit(node: CharacterLiteralNode) = CharacterValue(node.value)
        override fun visit(node: SymbolNode) = SymbolValue(node.name)

        override fun visit(node: VariableReferenceNode): InterpreterValue {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: BinaryOperationNode): InterpreterValue {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: IsNode): InterpreterValue {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: CallNode): InterpreterValue {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: PartialCallNode): InterpreterValue {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: FieldAccessNode): InterpreterValue {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: FunctionExpressionNode): InterpreterValue {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: IfNode): InterpreterValue {
            throw UnsupportedOperationException("not implemented")
        }

        override fun visit(node: WhenNode): InterpreterValue {
            throw UnsupportedOperationException("not implemented")
        }

    })
}
