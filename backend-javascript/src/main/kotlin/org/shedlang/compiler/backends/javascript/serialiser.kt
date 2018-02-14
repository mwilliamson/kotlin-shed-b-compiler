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

private fun line(text: String, indentation: Int) = indent(text, indentation) + "\n"
private fun indent(text: String, indentation: Int) = " ".repeat(indentation * INDENTATION_WIDTH) + text

internal fun serialise(node: JavascriptStatementNode, indentation: Int): String {
    fun line(text: String) = line(text, indentation)

    fun simpleStatement(text: String) = line(text + ";")

    return node.accept(object : JavascriptStatementNode.Visitor<String> {
        override fun visit(node: JavascriptReturnNode): String {
            val expression = serialise(node.expression, indentation = indentation)
            return simpleStatement("return " + expression)
        }

        override fun visit(node: JavascriptIfStatementNode): String {
            val conditionalBranches = node.conditionalBranches.mapIndexed { branchIndex, branch ->
                val keyword = if (branchIndex == 0) { "if" } else { "} else if" }
                val condition = serialise(branch.condition, indentation = indentation)
                val ifLine = line(keyword + " (" + condition + ") {")
                val body = serialiseBlock(branch.body, indentation)
                ifLine + body
            }

            val elseBranch = if (node.elseBranch.isEmpty()) {
                line("}")
            } else {
                line("} else {") + serialiseBlock(node.elseBranch, indentation) + line("}")
            }
            return conditionalBranches.joinToString("") + elseBranch
        }

        override fun visit(node: JavascriptExpressionStatementNode): String {
            return simpleStatement(serialise(node.expression, indentation = 1))
        }

        override fun visit(node: JavascriptFunctionDeclarationNode): String {
            val function = serialiseFunction(node.name, node, indentation = indentation)
            return indent(function, indentation = indentation) + "\n"
        }

        override fun visit(node: JavascriptConstNode): String {
            val expression = serialise(node.expression, indentation = indentation)
            return simpleStatement("const ${node.name} = $expression")
        }
    })
}

private fun serialiseFunction(
    name: String?,
    node: JavascriptFunctionNode,
    indentation: Int
): String {
    val signature = "function " +
        name.orEmpty() +
        "(" + node.arguments.joinToString(", ") + ") {\n"
    val body = serialiseBlock(node.body, indentation = indentation)
    return signature + body + indent("}", indentation = indentation)
}

private fun serialiseBlock(
    statements: List<JavascriptStatementNode>,
    indentation: Int
): String {
    return statements.map({ statement -> serialise(statement, indentation + 1) }).joinToString("")
}

internal fun serialise(node: JavascriptExpressionNode, indentation: Int) : String {
    return node.accept(object : JavascriptExpressionNode.Visitor<String> {
        override fun visit(node: JavascriptNullLiteralNode): String {
            return "null"
        }

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
            return serialiseSubExpression(node, node.left, associative = true, indentation = indentation) +
                " " +
                serialise(node.operator) +
                " " +
                serialiseSubExpression(node, node.right, associative = false, indentation = indentation)
        }

        override fun visit(node: JavascriptFunctionCallNode): String {
            val function = serialiseSubExpression(node, node.function, associative = true, indentation = indentation)
            val arguments = node.arguments
                .map({ argument -> serialise(argument, indentation = indentation) })
                .joinToString(", ")
            return "${function}(${arguments})"
        }

        override fun visit(node: JavascriptPropertyAccessNode): String {
            val receiver = serialiseSubExpression(node, node.receiver, associative = true, indentation = indentation)
            return receiver + "." + node.propertyName
        }

        override fun visit(node: JavascriptArrayLiteralNode): String {
            return "[" + node.elements.map { element -> serialise(element, indentation = indentation) }.joinToString(", ") + "]"
        }

        override fun visit(node: JavascriptObjectLiteralNode): String {
            if (node.properties.isEmpty()) {
                return "{}"
            } else {
                val open = "{\n"
                val properties = node.properties.entries.mapIndexed(fun(index, property): String {
                    val value = serialise(property.value, indentation = indentation + 1)
                    val comma = if (index == node.properties.size - 1) "" else ","
                    return line(
                        "${property.key}: ${value}${comma}",
                        indentation = indentation + 1
                    )
                }).joinToString("")
                val close = indent("}", indentation = indentation)
                return open + properties + close
            }
        }

        override fun visit(node: JavascriptAssignmentNode): String {
            return serialiseSubExpression(node, node.target, associative = false, indentation = indentation) + " = " + serialiseSubExpression(node, node.expression, associative = true, indentation = indentation)
        }

        override fun visit(node: JavascriptFunctionExpressionNode): String {
            return "(" + serialiseFunction(name = null, node = node, indentation = indentation) + ")"
        }
    })
}

private fun serialiseSubExpression(
    parentNode: JavascriptExpressionNode,
    node: JavascriptExpressionNode,
    associative: Boolean,
    indentation: Int
): String {
    return SubExpressionSerialiser<JavascriptExpressionNode>(
        serialise = { expression -> serialise(expression, indentation = indentation)},
        precedence = ::precedence
    ).serialiseSubExpression(
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

        override fun visit(node: JavascriptNullLiteralNode): Int {
            return 21
        }

        override fun visit(node: JavascriptArrayLiteralNode): Int {
            return 21
        }

        override fun visit(node: JavascriptObjectLiteralNode): Int {
            return 21
        }

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

        override fun visit(node: JavascriptAssignmentNode): Int {
            return 3
        }

        override fun visit(node: JavascriptFunctionExpressionNode): Int {
            return 21
        }
    })
}
