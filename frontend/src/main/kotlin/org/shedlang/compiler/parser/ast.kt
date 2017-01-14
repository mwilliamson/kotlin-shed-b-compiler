package org.shedlang.compiler.ast

interface Node {
    val source: Source
    val nodeId: Int
    val children: List<Node>
}

interface VariableBindingNode: Node {
    val name: String
}

interface ReferenceNode: Node {
    val name: String
}

interface Source

data class StringSource(val filename: String, val characterIndex: Int) : Source

private var nextId = 0

internal fun nextId() = nextId++

interface TypeNode : Node {
    interface Visitor<T> {
        fun visit(node: TypeReferenceNode): T
    }

    fun <T> accept(visitor: Visitor<T>): T
}

data class TypeReferenceNode(
    override val name: String,
    override val source: Source,
    override val nodeId: Int = nextId()
) : ReferenceNode, TypeNode {
    override val children: List<Node>
        get() = listOf()

    override fun <T> accept(visitor: TypeNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class ModuleNode(
    val name: String,
    val body: List<FunctionNode>,
    override val source: Source,
    override val nodeId: Int = nextId()
) : Node {
    override val children: List<Node>
        get() = body
}

data class FunctionNode(
    override val name: String,
    val arguments: List<ArgumentNode>,
    val returnType: TypeNode,
    val body: List<StatementNode>,
    override val source: Source,
    override val nodeId: Int = nextId()
) : VariableBindingNode {
    override val children: List<Node>
        get() = arguments + returnType + body
}

data class ArgumentNode(
    override val name: String,
    val type: TypeNode,
    override val source: Source,
    override val nodeId: Int = nextId()
) : VariableBindingNode, Node {
    override val children: List<Node>
        get() = listOf(type)
}

interface StatementNodeVisitor<T> {
    fun visit(node: BadStatementNode): T
    fun visit(node: ReturnNode): T
    fun visit(node: IfStatementNode): T
    fun visit(node: ExpressionStatementNode): T
}

interface StatementNode : Node {
    fun <T> accept(visitor: StatementNodeVisitor<T>): T
}

data class BadStatementNode(
    override val source: Source,
    override val nodeId: Int = nextId()
) : StatementNode {
    override val children: List<Node>
        get() = listOf()

    override fun <T> accept(visitor: StatementNodeVisitor<T>): T {
        return visitor.visit(this)
    }
}

data class ReturnNode(
    val expression: ExpressionNode,
    override val source: Source,
    override val nodeId: Int = nextId()
) : StatementNode {
    override val children: List<Node>
        get() = listOf(expression)

    override fun <T> accept(visitor: StatementNodeVisitor<T>): T {
        return visitor.visit(this)
    }
}

data class IfStatementNode(
    val condition: ExpressionNode,
    val trueBranch: List<StatementNode>,
    val falseBranch: List<StatementNode>,
    override val source: Source,
    override val nodeId: Int = nextId()
) : StatementNode {
    override val children: List<Node>
        get() = listOf(condition) + trueBranch + falseBranch

    override fun <T> accept(visitor: StatementNodeVisitor<T>): T {
        return visitor.visit(this)
    }
}

data class ExpressionStatementNode(
    val expression: ExpressionNode,
    override val source: Source,
    override val nodeId: Int = nextId()
): StatementNode {
    override val children: List<Node>
        get() = listOf(expression)

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
    override val source: Source,
    override val nodeId: Int = nextId()
): ExpressionNode {
    override val children: List<Node>
        get() = listOf()

    override fun <T> accept(visitor: ExpressionNodeVisitor<T>): T {
        return visitor.visit(this)
    }
}

data class IntegerLiteralNode(
    val value: Int,
    override val source: Source,
    override val nodeId: Int = nextId()
) : ExpressionNode {
    override val children: List<Node>
        get() = listOf()

    override fun <T> accept(visitor: ExpressionNodeVisitor<T>): T {
        return visitor.visit(this)
    }
}

data class VariableReferenceNode(
    override val name: String,
    override val source: Source,
    override val nodeId: Int = nextId()
) : ReferenceNode, ExpressionNode {
    override val children: List<Node>
        get() = listOf()

    override fun <T> accept(visitor: ExpressionNodeVisitor<T>): T {
        return visitor.visit(this)
    }
}

data class BinaryOperationNode(
    val operator: Operator,
    val left: ExpressionNode,
    val right: ExpressionNode,
    override val source: Source,
    override val nodeId: Int = nextId()
) : ExpressionNode {
    override val children: List<Node>
        get() = listOf(left, right)

    override fun <T> accept(visitor: ExpressionNodeVisitor<T>): T {
        return visitor.visit(this)
    }
}

data class FunctionCallNode(
    val function: ExpressionNode,
    val arguments: List<ExpressionNode>,
    override val source: Source,
    override val nodeId: Int = nextId()
) : ExpressionNode {
    override val children: List<Node>
        get() = listOf(function) + arguments

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
