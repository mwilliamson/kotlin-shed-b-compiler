package org.shedlang.compiler.backends.python.ast

import org.shedlang.compiler.ast.Source

interface PythonNode {
    val source: Source
}

data class PythonModuleNode(
    val body: List<PythonStatementNode>,
    override val source: Source
) : PythonNode

interface PythonStatementNode : PythonNode {
    interface Visitor<T> {
        fun visit(node: PythonImportFromNode): T
        fun visit(node: PythonClassNode): T
        fun visit(node: PythonFunctionNode): T
        fun visit(node: PythonExpressionStatementNode): T
        fun visit(node: PythonReturnNode): T
        fun visit(node: PythonIfStatementNode): T
        fun visit(node: PythonPassNode): T
        fun visit(node: PythonAssignmentNode): T
    }

    fun <T> accept(visitor: Visitor<T>): T
}

data class PythonImportFromNode(
    val module: String,
    val names: List<String>,
    override val source: Source
): PythonStatementNode {
    override fun <T> accept(visitor: PythonStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class PythonClassNode(
    val name: String,
    val body: List<PythonStatementNode>,
    override val source: Source
): PythonStatementNode {
    override fun <T> accept(visitor: PythonStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class PythonFunctionNode(
    val name: String,
    val arguments: List<String>,
    val body: List<PythonStatementNode>,
    override val source: Source
): PythonStatementNode {
    override fun <T> accept(visitor: PythonStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class PythonLambdaNode(
    val arguments: List<String>,
    val body: PythonExpressionNode,
    override val source: Source
): PythonExpressionNode {
    override fun <T> accept(visitor: PythonExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class PythonExpressionStatementNode(
    val expression: PythonExpressionNode,
    override val source: Source
) : PythonStatementNode {
    override fun <T> accept(visitor: PythonStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class PythonReturnNode(
    val expression: PythonExpressionNode,
    override val source: Source
) : PythonStatementNode {
    override fun <T> accept(visitor: PythonStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class PythonIfStatementNode(
    val conditionalBranches: List<PythonConditionalBranchNode>,
    val elseBranch: List<PythonStatementNode>,
    override val source: Source
) : PythonStatementNode {
    override fun <T> accept(visitor: PythonStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class PythonConditionalBranchNode(
    val condition: PythonExpressionNode,
    val body: List<PythonStatementNode>,
    override val source: Source
): PythonNode

data class PythonPassNode(
    override val source: Source
) : PythonStatementNode {
    override fun <T> accept(visitor: PythonStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class PythonAssignmentNode(
    val target: PythonExpressionNode,
    val expression: PythonExpressionNode,
    override val source: Source
): PythonStatementNode {
    override fun <T> accept(visitor: PythonStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

interface PythonExpressionNode : PythonNode {
    interface Visitor<T> {
        fun visit(node: PythonNoneLiteralNode): T
        fun visit(node: PythonBooleanLiteralNode): T
        fun visit(node: PythonIntegerLiteralNode): T
        fun visit(node: PythonStringLiteralNode): T
        fun visit(node: PythonVariableReferenceNode): T
        fun visit(node: PythonBinaryOperationNode): T
        fun visit(node: PythonFunctionCallNode): T
        fun visit(node: PythonAttributeAccessNode): T
        fun visit(node: PythonLambdaNode): T
    }

    fun <T> accept(visitor: Visitor<T>): T
}

data class PythonNoneLiteralNode(
    override val source: Source
): PythonExpressionNode {
    override fun <T> accept(visitor: PythonExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class PythonBooleanLiteralNode(
    val value: Boolean,
    override val source: Source
): PythonExpressionNode {
    override fun <T> accept(visitor: PythonExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class PythonIntegerLiteralNode(
    val value: Int,
    override val source: Source
): PythonExpressionNode {
    override fun <T> accept(visitor: PythonExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class PythonStringLiteralNode(
    val value: String,
    override val source: Source
): PythonExpressionNode {
    override fun <T> accept(visitor: PythonExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class PythonVariableReferenceNode(
    val name: String,
    override val source: Source
): PythonExpressionNode {
    override fun <T> accept(visitor: PythonExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class PythonBinaryOperationNode(
    val operator: PythonOperator,
    val left: PythonExpressionNode,
    val right: PythonExpressionNode,
    override val source: Source
): PythonExpressionNode {
    override fun <T> accept(visitor: PythonExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

enum class PythonOperator {
    EQUALS,
    ADD,
    SUBTRACT,
    MULTIPLY
}

data class PythonFunctionCallNode(
    val function: PythonExpressionNode,
    val arguments: List<PythonExpressionNode>,
    val keywordArguments: List<Pair<String, PythonExpressionNode>>,
    override val source: Source
): PythonExpressionNode {
    override fun <T> accept(visitor: PythonExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class PythonAttributeAccessNode(
    val receiver: PythonExpressionNode,
    val attributeName: String,
    override val source: Source
): PythonExpressionNode {
    override fun <T> accept(visitor: PythonExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}
