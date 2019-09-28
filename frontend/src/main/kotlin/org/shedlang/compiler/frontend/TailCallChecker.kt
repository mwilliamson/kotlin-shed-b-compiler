package org.shedlang.compiler.frontend

import org.shedlang.compiler.ResolvedReferences
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.typechecker.InvalidTailCall

internal fun checkTailCalls(moduleNode: ModuleNode, references: ResolvedReferences) {
    moduleNode.descendants()
        .filterIsInstance<FunctionNode>()
        .forEach { function -> checkTailCalls(function, references = references) }
}

internal fun checkTailCalls(function: FunctionNode, references: ResolvedReferences) {
    checkBlock(function.body, function = function, references = references)
}

private fun checkBlock(block: Block, function: FunctionNode, references: ResolvedReferences) {
    for (statement in block.statements) {
        checkStatement(statement, function = function, references = references)
    }
}

private fun checkStatement(statement: FunctionStatementNode, function: FunctionNode, references: ResolvedReferences) {
    if (statement is ExpressionStatementNode) {
        val expression = statement.expression
        if (statement.type == ExpressionStatementNode.Type.TAILREC_RETURN) {
            checkTailCall(expression, function = function, references = references)
        } else if (statement.type == ExpressionStatementNode.Type.RETURN) {
            val blocks = if (expression is IfNode) {
                expression.branchBodies
            } else if (expression is WhenNode) {
                expression.branchBodies
            } else {
                listOf()
            }

            for (block in blocks) {
                checkBlock(block, function = function, references = references)
            }
        }
    }
}

private fun checkTailCall(expression: ExpressionNode, function: FunctionNode, references: ResolvedReferences) {
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
