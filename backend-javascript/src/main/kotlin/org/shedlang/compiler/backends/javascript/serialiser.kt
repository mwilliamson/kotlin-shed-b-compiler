package org.shedlang.compiler.backends.javascript

import org.shedlang.compiler.backends.SubExpressionSerialiser
import org.shedlang.compiler.backends.javascript.ast.*
import org.shedlang.compiler.backends.serialiseCStringLiteral

private val INDENTATION_WIDTH = 4

internal fun serialise(node: JavascriptModuleNode): String {
    return node.body
        .map({ statement -> serialise(statement, indentation = 0) })
        .joinToString("")
}

internal fun serialise(node: JavascriptStatementNode, indentation: Int): String {
    fun line(text: String) = " ".repeat(indentation * INDENTATION_WIDTH) + text + "\n"

    fun simpleStatement(text: String) = line(text + ";")

    return node.accept(object : JavascriptStatementNode.Visitor<String> {
        override fun visit(node: JavascriptReturnNode): String {
            return simpleStatement("return " + serialise(node.expression))
        }

        override fun visit(node: JavascriptIfStatementNode): String {
            val condition = line("if (" + serialise(node.condition) + ") {")
            val trueBranch = serialiseBlock(node.trueBranch, indentation)
            val falseBranch = if (node.falseBranch.isEmpty()) {
                line("}")
            } else {
                line("} else {") + serialiseBlock(node.falseBranch, indentation) + line("}")
            }
            return condition + trueBranch + falseBranch
        }

        override fun visit(node: JavascriptExpressionStatementNode): String {
            return simpleStatement(serialise(node.expression))
        }

        override fun visit(node: JavascriptFunctionNode): String {
            val signature = line(
                "function " +
                    node.name +
                    "(" + node.arguments.joinToString(", ") + ") {"
            )
            val body = serialiseBlock(node.body, indentation = indentation)
            return signature + body + line("}")
        }

        override fun visit(node: JavascriptConstNode): String {
            return simpleStatement("const ${node.name} = ${serialise(node.expression)}")
        }
    })
}

private fun serialiseBlock(
    statements: List<JavascriptStatementNode>,
    indentation: Int
): String {
    return statements.map({ statement -> serialise(statement, indentation + 1) }).joinToString("")
}

internal fun serialise(node: JavascriptExpressionNode) : String {
    return node.accept(object : JavascriptExpressionNode.Visitor<String> {
        override fun visit(node: JavascriptBooleanLiteralNode): String {
            return if (node.value) "true" else "false"
        }

        override fun visit(node: JavascriptIntegerLiteralNode): String {
            return node.value.toString()
        }

        override fun visit(node: JavascriptStringLiteralNode): String {
            return serialiseCStringLiteral(node.value)
        }

        override fun visit(node: JavascriptVariableReferenceNode): String {
            return node.name
        }

        override fun visit(node: JavascriptBinaryOperationNode): String {
            return serialiseSubExpression(node, node.left, associative = true) +
                " " +
                serialise(node.operator) +
                " " +
                serialiseSubExpression(node, node.right, associative = false)
        }

        override fun visit(node: JavascriptFunctionCallNode): String {
            return serialiseSubExpression(node, node.function, associative = true) +
                "(" +
                node.arguments.map(::serialise).joinToString(", ") +
                ")"
        }

        override fun visit(node: JavascriptPropertyAccessNode): String {
            val receiver = serialiseSubExpression(node, node.receiver, associative = true)
            return receiver + "." + node.propertyName
        }
    })
}

val subExpressionSerialiser = SubExpressionSerialiser<JavascriptExpressionNode>(
    serialise = ::serialise,
    precedence = ::precedence
)

private fun serialiseSubExpression(
    parentNode: JavascriptExpressionNode,
    node: JavascriptExpressionNode,
    associative: Boolean
): String {
    return subExpressionSerialiser.serialiseSubExpression(
        parentNode = parentNode,
        node = node,
        associative = associative
    )
}

private fun serialise(operator: JavascriptOperator) = when(operator) {
    JavascriptOperator.EQUALS -> "==="
    JavascriptOperator.ADD -> "+"
    JavascriptOperator.SUBTRACT -> "-"
    JavascriptOperator.MULTIPLY -> "*"
}

private fun precedence(node: JavascriptExpressionNode): Int {
    return node.accept(object : JavascriptExpressionNode.Visitor<Int> {
        override fun visit(node: JavascriptBooleanLiteralNode): Int {
            return 21
        }

        override fun visit(node: JavascriptIntegerLiteralNode): Int {
            return 21
        }

        override fun visit(node: JavascriptStringLiteralNode): Int {
            return 21
        }

        override fun visit(node: JavascriptVariableReferenceNode): Int {
            return 21
        }

        override fun visit(node: JavascriptBinaryOperationNode): Int {
            return when(node.operator) {
                JavascriptOperator.EQUALS -> 10
                JavascriptOperator.ADD -> 13
                JavascriptOperator.SUBTRACT -> 13
                JavascriptOperator.MULTIPLY -> 14
            }
        }

        override fun visit(node: JavascriptFunctionCallNode): Int {
            return 18
        }

        override fun visit(node: JavascriptPropertyAccessNode): Int {
            return 18
        }
    })
}
