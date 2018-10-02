package org.shedlang.compiler.backends.javascript.ast

import org.shedlang.compiler.ast.Source
import java.math.BigInteger

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
        fun visit(node: JavascriptFunctionDeclarationNode): T
        fun visit(node: JavascriptConstNode): T
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
    val conditionalBranches: List<JavascriptConditionalBranchNode>,
    val elseBranch: List<JavascriptStatementNode>,
    override val source: Source
) : JavascriptStatementNode {
    override fun <T> accept(visitor: JavascriptStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class JavascriptConditionalBranchNode(
    val condition: JavascriptExpressionNode,
    val body: List<JavascriptStatementNode>,
    override val source: Source
) : JavascriptNode

data class JavascriptExpressionStatementNode(
    val expression: JavascriptExpressionNode,
    override val source: Source
): JavascriptStatementNode {
    override fun <T> accept(visitor: JavascriptStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class JavascriptFunctionDeclarationNode(
    val name: String,
    override val parameters: List<String>,
    override val body: List<JavascriptStatementNode>,
    override val source: Source
) : JavascriptFunctionNode, JavascriptStatementNode {
    override fun <T> accept(visitor: JavascriptStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class JavascriptFunctionExpressionNode(
    override val parameters: List<String>,
    override val body: List<JavascriptStatementNode>,
    override val source: Source
) : JavascriptFunctionNode, JavascriptExpressionNode {
    override fun <T> accept(visitor: JavascriptExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

interface JavascriptFunctionNode {
    val parameters: List<String>
    val body: List<JavascriptStatementNode>
}

data class JavascriptConstNode(
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
        fun visit(node: JavascriptNullLiteralNode): T
        fun visit(node: JavascriptBooleanLiteralNode): T
        fun visit(node: JavascriptIntegerLiteralNode): T
        fun visit(node: JavascriptStringLiteralNode): T
        fun visit(node: JavascriptVariableReferenceNode): T
        fun visit(node: JavascriptUnaryOperationNode): T
        fun visit(node: JavascriptBinaryOperationNode): T
        fun visit(node: JavascriptFunctionCallNode): T
        fun visit(node: JavascriptPropertyAccessNode): T
        fun visit(node: JavascriptArrayLiteralNode): T
        fun visit(node: JavascriptObjectLiteralNode): T
        fun visit(node: JavascriptAssignmentNode): T
        fun visit(node: JavascriptFunctionExpressionNode): T
    }

    fun <T> accept(visitor: JavascriptExpressionNode.Visitor<T>): T
}

data class JavascriptNullLiteralNode(
    override val source: Source
): JavascriptExpressionNode {
    override fun <T> accept(visitor: JavascriptExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
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
    val value: BigInteger,
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

data class JavascriptUnaryOperationNode(
    val operator: JavascriptUnaryOperator,
    val operand: JavascriptExpressionNode,
    override val source: Source
) : JavascriptExpressionNode {
    override fun <T> accept(visitor: JavascriptExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class JavascriptBinaryOperationNode(
    val operator: JavascriptBinaryOperator,
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

data class JavascriptPropertyAccessNode(
    val receiver: JavascriptExpressionNode,
    val propertyName: String,
    override val source: Source
) : JavascriptExpressionNode {
    override fun <T> accept(visitor: JavascriptExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class JavascriptArrayLiteralNode(
    val elements: List<JavascriptExpressionNode>,
    override val source: Source
): JavascriptExpressionNode {
    override fun <T> accept(visitor: JavascriptExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class JavascriptObjectLiteralNode(
    val properties: Map<String, JavascriptExpressionNode>,
    override val source: Source
): JavascriptExpressionNode {
    override fun <T> accept(visitor: JavascriptExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class JavascriptAssignmentNode(
    val target: JavascriptExpressionNode,
    val expression: JavascriptExpressionNode,
    override val source: Source
): JavascriptExpressionNode {
    override fun <T> accept(visitor: JavascriptExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

enum class JavascriptUnaryOperator {
    NOT
}

enum class JavascriptBinaryOperator {
    EQUALS,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    ADD,
    SUBTRACT,
    MULTIPLY
}
