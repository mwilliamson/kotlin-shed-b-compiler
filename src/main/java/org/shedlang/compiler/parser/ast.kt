package org.shedlang.compiler.ast

interface Node {
    val location: SourceLocation
}

data class SourceLocation(val filename: String, val characterIndex: Int)

interface TypeNode : Node {
    interface Visitor<T> {
        fun visit(node: TypeReferenceNode): T
    }

    fun <T> accept(visitor: Visitor<T>): T
}
data class TypeReferenceNode(
    val name: String,
    override val location: SourceLocation
) : TypeNode {
    override fun <T> accept(visitor: TypeNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class ModuleNode(
    val name: String,
    val body: List<FunctionNode>,
    override val location: SourceLocation
) : Node

data class FunctionNode(
    val name: String,
    val arguments: List<ArgumentNode>,
    val returnType: TypeNode,
    val body: List<StatementNode>,
    override val location: SourceLocation
) : Node

data class ArgumentNode(
    val name: String,
    val type: TypeNode,
    override val location: SourceLocation
) : Node

interface StatementNodeVisitor<T> {
    fun visit(node: ReturnNode): T
    fun visit(node: IfStatementNode): T
}

interface StatementNode : Node {
    fun <T> accept(visitor: StatementNodeVisitor<T>): T
}

data class ReturnNode(
    val expression: ExpressionNode,
    override val location: SourceLocation
) : StatementNode {
    override fun <T> accept(visitor: StatementNodeVisitor<T>): T {
        return visitor.visit(this)
    }
}

data class IfStatementNode(
    val condition: ExpressionNode,
    val trueBranch: List<StatementNode>,
    val falseBranch: List<StatementNode>,
    override val location: SourceLocation
) : StatementNode {
    override fun <T> accept(visitor: StatementNodeVisitor<T>): T {
        return visitor.visit(this)
    }
}

interface ExpressionNodeVisitor<T> {
    fun visit(node: BooleanLiteralNode): T
    fun visit(node: IntegerLiteralNode): T
    fun visit(node: VariableReferenceNode): T
    fun visit(node: BinaryOperationNode): T
    fun visit(node: FunctionCallNode): T
}

interface ExpressionNode : Node {
    fun <T> accept(visitor: ExpressionNodeVisitor<T>): T
}

data class BooleanLiteralNode(
    val value: Boolean,
    override val location: SourceLocation
): ExpressionNode {
    override fun <T> accept(visitor: ExpressionNodeVisitor<T>): T {
        return visitor.visit(this)
    }
}

data class IntegerLiteralNode(
    val value: Int,
    override val location: SourceLocation
) : ExpressionNode {
    override fun <T> accept(visitor: ExpressionNodeVisitor<T>): T {
        return visitor.visit(this)
    }
}

data class VariableReferenceNode(
    val name: String,
    override val location: SourceLocation
) : ExpressionNode {
    override fun <T> accept(visitor: ExpressionNodeVisitor<T>): T {
        return visitor.visit(this)
    }
}

data class BinaryOperationNode(
    val operator: Operator,
    val left: ExpressionNode,
    val right: ExpressionNode,
    override val location: SourceLocation
) : ExpressionNode {
    override fun <T> accept(visitor: ExpressionNodeVisitor<T>): T {
        return visitor.visit(this)
    }
}

data class FunctionCallNode(
    val function: ExpressionNode,
    val arguments: List<ExpressionNode>,
    override val location: SourceLocation
) : ExpressionNode {
    override fun <T> accept(visitor: ExpressionNodeVisitor<T>): T {
        return visitor.visit(this)
    }
}

enum class Operator {
    EQUALS,
    ADD,
    SUBTRACT,
    MULTIPLY
}
