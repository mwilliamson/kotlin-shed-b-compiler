package org.shedlang.compiler.backends.javascript.ast

import org.shedlang.compiler.ast.Source

interface JavascriptNode {
    val source: Source
}

data class JavascriptModuleNode(
    val body: List<JavascriptStatementNode>,
    override val source: Source
) : JavascriptNode

interface JavascriptStatementNode : JavascriptNode {
    interface Visitor<T> {
        fun visit(node: JavascriptReturnNode): T
        fun visit(node: JavascriptIfStatementNode): T
        fun visit(node: JavascriptExpressionStatementNode): T
        fun visit(node: JavascriptFunctionNode): T
        fun visit(node: JavascriptLetNode): T
    }

    fun <T> accept(visitor: JavascriptStatementNode.Visitor<T>): T
}

data class JavascriptReturnNode(
    val expression: JavascriptExpressionNode,
    override val source: Source
) : JavascriptStatementNode {
    override fun <T> accept(visitor: JavascriptStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class JavascriptIfStatementNode(
    val condition: JavascriptExpressionNode,
    val trueBranch: List<JavascriptStatementNode>,
    val falseBranch: List<JavascriptStatementNode>,
    override val source: Source
) : JavascriptStatementNode {
    override fun <T> accept(visitor: JavascriptStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class JavascriptExpressionStatementNode(
    val expression: JavascriptExpressionNode,
    override val source: Source
): JavascriptStatementNode {
    override fun <T> accept(visitor: JavascriptStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class JavascriptFunctionNode(
    val name: String,
    val arguments: List<String>,
    val body: List<JavascriptStatementNode>,
    override val source: Source
) : JavascriptStatementNode {
    override fun <T> accept(visitor: JavascriptStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class JavascriptLetNode(
    val name: String,
    val expression: JavascriptExpressionNode,
    override val source: Source
) : JavascriptStatementNode {
    override fun <T> accept(visitor: JavascriptStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

interface JavascriptExpressionNode : JavascriptNode {
    interface Visitor<T> {
        fun visit(node: JavascriptBooleanLiteralNode): T
        fun visit(node: JavascriptIntegerLiteralNode): T
        fun visit(node: JavascriptStringLiteralNode): T
        fun visit(node: JavascriptVariableReferenceNode): T
        fun visit(node: JavascriptBinaryOperationNode): T
        fun visit(node: JavascriptFunctionCallNode): T
    }

    fun <T> accept(visitor: JavascriptExpressionNode.Visitor<T>): T
}

data class JavascriptBooleanLiteralNode(
    val value: Boolean,
    override val source: Source
): JavascriptExpressionNode {
    override fun <T> accept(visitor: JavascriptExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class JavascriptIntegerLiteralNode(
    val value: Int,
    override val source: Source
) : JavascriptExpressionNode {
    override fun <T> accept(visitor: JavascriptExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class JavascriptStringLiteralNode(
    val value: String,
    override val source: Source
) : JavascriptExpressionNode {
    override fun <T> accept(visitor: JavascriptExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class JavascriptVariableReferenceNode(
    val name: String,
    override val source: Source
) : JavascriptExpressionNode {
    override fun <T> accept(visitor: JavascriptExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class JavascriptBinaryOperationNode(
    val operator: JavascriptOperator,
    val left: JavascriptExpressionNode,
    val right: JavascriptExpressionNode,
    override val source: Source
) : JavascriptExpressionNode {
    override fun <T> accept(visitor: JavascriptExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class JavascriptFunctionCallNode(
    val function: JavascriptExpressionNode,
    val arguments: List<JavascriptExpressionNode>,
    override val source: Source
) : JavascriptExpressionNode {
    override fun <T> accept(visitor: JavascriptExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

enum class JavascriptOperator {
    EQUALS,
    ADD,
    SUBTRACT,
    MULTIPLY
}
