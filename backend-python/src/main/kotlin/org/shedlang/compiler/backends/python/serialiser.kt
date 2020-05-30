package org.shedlang.compiler.backends.python

import org.shedlang.compiler.backends.SubExpressionSerialiser
import org.shedlang.compiler.backends.python.ast.*
import org.shedlang.compiler.backends.serialiseCStringLiteral

private val INDENTATION_WIDTH = 4

fun serialise(node: PythonModuleNode) : String {
    return node.body.map({ statement -> serialise(statement) }).joinToString("")
}

internal fun serialise(node: PythonStatementNode, indentation: Int = 0): String {
    fun line(text: String) = " ".repeat(indentation * INDENTATION_WIDTH) + text + "\n"

    return node.accept(object : PythonStatementNode.Visitor<String> {
        override fun visit(node: PythonImportFromNode): String {
            val names = node.names.map { (name, alias) ->
                if (name == alias) {
                    name
                } else {
                    name + " as " + alias
                }
            }
            return line("from " + node.module + " import " + names.joinToString(", "))
        }

        override fun visit(node: PythonClassNode): String {
            val declaration = line("class ${node.name}(object):")
            val body = serialiseBlock(node, node.body, indentation)
            return ensureTrailingBlankLine(declaration + body)
        }

        override fun visit(node: PythonFunctionNode): String {
            val decorators = node.decorators
                .map { decorator -> line("@" + serialise(decorator)) }
                .joinToString("")
            val signature = line("def " + node.name + "(" + node.parameters.joinToString(", ") + "):")
            val body = serialiseBlock(node, node.body, indentation)
            return ensureTrailingBlankLine(decorators + signature + body)
        }

        override fun visit(node: PythonExpressionStatementNode): String {
            return line(serialise(node.expression))
        }

        override fun visit(node: PythonReturnNode): String {
            return line("return " + serialise(node.expression))
        }

        override fun visit(node: PythonIfStatementNode): String {
            val conditionalBranches = node.conditionalBranches.mapIndexed { branchIndex, branch ->
                val keyword = if (branchIndex == 0) { "if" } else { "elif" }
                val condition = line(keyword + " " + serialise(branch.condition) + ":")
                val body = serialiseBlock(node, branch.body, indentation)
                condition + body
            }
            val elseBranch = if (node.elseBranch.isEmpty()) {
                ""
            } else {
                line("else:") + serialiseBlock(node, node.elseBranch, indentation)
            }
            return conditionalBranches.joinToString("") + elseBranch
        }

        override fun visit(node: PythonPassNode): String {
            return line("pass")
        }

        override fun visit(node: PythonAssignmentNode): String {
            return line("${serialise(node.target)} = ${serialise(node.expression)}")
        }

        override fun visit(node: PythonWhileNode): String {
            val body = serialiseBlock(node, node.body, indentation)
            return line("while " + serialise(node.condition) + ":") + body
        }
    })
}

private fun serialiseBlock(
    parent: PythonNode,
    statements: List<PythonStatementNode>,
    indentation: Int
): String {
    return if (statements.isEmpty()) {
        listOf(PythonPassNode(source = parent.source))
    } else {
        statements
    }.map({ statement -> serialise(statement, indentation + 1) }).joinToString("")
}

internal fun serialise(node: PythonExpressionNode): String {
    return node.accept(object : PythonExpressionNode.Visitor<String>{
        override fun visit(node: PythonNoneLiteralNode): String {
            return "None"
        }

        override fun visit(node: PythonBooleanLiteralNode): String {
            return if (node.value) "True" else "False"
        }

        override fun visit(node: PythonIntegerLiteralNode): String {
            return node.value.toString()
        }

        override fun visit(node: PythonStringLiteralNode): String {
            return serialiseCStringLiteral(node.value);
        }

        override fun visit(node: PythonVariableReferenceNode): String {
            return node.name
        }

        override fun visit(node: PythonTupleNode): String {
            val elements = node.members.map(::serialise).joinToString(", ")
            val trailingComma = if (node.members.size == 1) {
                ", "
            } else {
                ""
            }
            return "(" + elements + trailingComma + ")"
        }

        override fun visit(node: PythonUnaryOperationNode): String {
            val prefix = when (node.operator) {
                PythonUnaryOperator.MINUS -> "-"
                PythonUnaryOperator.NOT -> "not "
            }
            return prefix + serialiseSubExpression(node, node.operand, associative = true)
        }

        override fun visit(node: PythonBinaryOperationNode): String {
            return serialiseSubExpression(node, node.left, associative = isLeftAssociative(node.operator)) +
                " " +
                serialise(node.operator) +
                " " +
                serialiseSubExpression(node, node.right, associative = false)
        }

        override fun visit(node: PythonConditionalOperationNode): String {
            val condition = serialise(node.condition)
            val trueExpression = serialiseSubExpression(node, node.trueExpression, associative = false)
            val falseExpression = serialiseSubExpression(node, node.falseExpression, associative = false)

            return "$trueExpression if $condition else $falseExpression"
        }

        override fun visit(node: PythonFunctionCallNode): String {
            val receiver = serialiseSubExpression(node, node.function, associative = true)
            val positionals = node.arguments.map(::serialise)
            val keywords = node.keywordArguments.map({ (key, value) -> "${key}=${serialise(value)}" })
            val arguments = (positionals + keywords).joinToString(", ")
            return "${receiver}(${arguments})"
        }

        override fun visit(node: PythonAttributeAccessNode): String {
            val receiver = serialiseSubExpression(node, node.receiver, associative = true)
            return "${receiver}.${node.attributeName}"
        }

        override fun visit(node: PythonLambdaNode): String {
            val parameters = node.parameters.joinToString(", ")
            return "lambda ${parameters}: ${serialise(node.body)}"
        }
    })
}

val subExpressionSerialiser = SubExpressionSerialiser<PythonExpressionNode>(
    serialise = ::serialise,
    precedence = ::precedence
)

private fun serialiseSubExpression(
    parentNode: PythonExpressionNode,
    node: PythonExpressionNode,
    associative: Boolean
): String {
    return subExpressionSerialiser.serialiseSubExpression(
        parentNode = parentNode,
        node = node,
        associative = associative
    )
}

private fun serialise(operator: PythonBinaryOperator) = when(operator) {
    PythonBinaryOperator.EQUALS -> "=="
    PythonBinaryOperator.NOT_EQUAL -> "!="
    PythonBinaryOperator.LESS_THAN-> "<"
    PythonBinaryOperator.LESS_THAN_OR_EQUAL -> "<="
    PythonBinaryOperator.GREATER_THAN -> ">"
    PythonBinaryOperator.GREATER_THAN_OR_EQUAL -> ">="
    PythonBinaryOperator.ADD -> "+"
    PythonBinaryOperator.SUBTRACT -> "-"
    PythonBinaryOperator.MULTIPLY -> "*"
    PythonBinaryOperator.AND -> "and"
    PythonBinaryOperator.OR -> "or"
}

private fun isLeftAssociative(operator: PythonBinaryOperator) = when(operator) {
    PythonBinaryOperator.EQUALS -> false
    PythonBinaryOperator.NOT_EQUAL -> false
    PythonBinaryOperator.LESS_THAN -> false
    PythonBinaryOperator.LESS_THAN_OR_EQUAL -> false
    PythonBinaryOperator.GREATER_THAN -> false
    PythonBinaryOperator.GREATER_THAN_OR_EQUAL -> false
    PythonBinaryOperator.ADD -> true
    PythonBinaryOperator.SUBTRACT -> true
    PythonBinaryOperator.MULTIPLY -> true
    PythonBinaryOperator.AND -> true
    PythonBinaryOperator.OR -> true
}

private fun precedence(node: PythonExpressionNode): Int {
    return node.accept(object : PythonExpressionNode.Visitor<Int> {
        override fun visit(node: PythonNoneLiteralNode): Int {
            return 18
        }

        override fun visit(node: PythonBooleanLiteralNode): Int {
            return 18
        }

        override fun visit(node: PythonIntegerLiteralNode): Int {
            return 18
        }

        override fun visit(node: PythonStringLiteralNode): Int {
            return 18
        }

        override fun visit(node: PythonVariableReferenceNode): Int {
            return 18
        }

        override fun visit(node: PythonTupleNode): Int {
            return 18
        }

        override fun visit(node: PythonUnaryOperationNode): Int {
            return 5
        }

        override fun visit(node: PythonBinaryOperationNode): Int {
            return when(node.operator) {
                PythonBinaryOperator.OR -> 3
                PythonBinaryOperator.AND -> 4
                PythonBinaryOperator.EQUALS -> 6
                PythonBinaryOperator.NOT_EQUAL -> 6
                PythonBinaryOperator.LESS_THAN -> 6
                PythonBinaryOperator.LESS_THAN_OR_EQUAL -> 6
                PythonBinaryOperator.GREATER_THAN -> 6
                PythonBinaryOperator.GREATER_THAN_OR_EQUAL -> 6
                PythonBinaryOperator.ADD -> 11
                PythonBinaryOperator.SUBTRACT -> 11
                PythonBinaryOperator.MULTIPLY -> 12
            }
        }

        override fun visit(node: PythonConditionalOperationNode): Int {
            return 2
        }

        override fun visit(node: PythonFunctionCallNode): Int {
            return 16
        }

        override fun visit(node: PythonAttributeAccessNode): Int {
            return 16
        }

        override fun visit(node: PythonLambdaNode): Int {
            return 1
        }
    })
}

private fun ensureTrailingBlankLine(value: String): String {
    if (value.endsWith("\n\n")) {
        return value
    } else if (value.endsWith("\n")) {
        return value + "\n"
    } else {
        return value + "\n\n"
    }
}
