package org.shedlang.compiler.frontend

import org.shedlang.compiler.ResolvedReferences
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.typechecker.InvalidTailCall

internal fun checkTailCalls(moduleNode: ModuleNode, references: ResolvedReferences) {
    val checkedTailCalls = mutableSetOf<Int>()

    moduleNode.descendants()
        .filterIsInstance<FunctionNode>()
        .forEach { function -> checkTailCalls(function, references = references, checkedTailCalls = checkedTailCalls) }

    moduleNode.descendants()
        .filterIsInstance<ExpressionStatementNode>()
        .filter { statement -> statement.type == ExpressionStatementNode.Type.TAILREC_RETURN && !checkedTailCalls.contains(statement.nodeId) }
        .forEach { statement ->
            throw InvalidTailCall(source = statement.source)
        }
}

private fun checkTailCalls(function: FunctionNode, references: ResolvedReferences, checkedTailCalls: MutableSet<Int>) {
    checkBlock(function.body, function = function, references = references, checkedTailCalls = checkedTailCalls)
}

private fun checkBlock(block: Block, function: FunctionNode, references: ResolvedReferences, checkedTailCalls: MutableSet<Int>) {
    for (statement in block.statements) {
        checkStatement(statement, function = function, references = references, checkedTailCalls = checkedTailCalls)
    }
}

private fun checkStatement(statement: FunctionStatementNode, function: FunctionNode, references: ResolvedReferences, checkedTailCalls: MutableSet<Int>) {
    if (statement is ExpressionStatementNode) {
        val expression = statement.expression
        if (statement.type == ExpressionStatementNode.Type.TAILREC_RETURN) {
            checkTailCall(expression, function = function, references = references, checkedTailCalls = checkedTailCalls)
            checkedTailCalls.add(statement.nodeId)
        } else if (statement.type == ExpressionStatementNode.Type.RETURN) {
            val blocks = expressionBranches(expression)

            if (blocks != null) {
                for (block in blocks) {
                    checkBlock(block, function = function, references = references, checkedTailCalls = checkedTailCalls)
                }
            }
        }
    }
}

private fun checkTailCall(expression: ExpressionNode, function: FunctionNode, references: ResolvedReferences, checkedTailCalls: MutableSet<Int>) {
    if (expression !is CallNode) {
        throw InvalidTailCall(source = expression.source)
    }

    val receiver = expression.receiver
    if (receiver !is ReferenceNode) {
        throw InvalidTailCall(source = expression.source)
    }

    if (references[receiver].nodeId != function.nodeId) {
        throw InvalidTailCall(source = expression.source)
    }
}
