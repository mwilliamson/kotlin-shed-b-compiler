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
    for (statement in function.body.statements) {
        if (statement is ExpressionStatementNode && statement.type == ExpressionStatementNode.Type.TAILREC_RETURN) {
            val expression = statement.expression
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
    }
}
