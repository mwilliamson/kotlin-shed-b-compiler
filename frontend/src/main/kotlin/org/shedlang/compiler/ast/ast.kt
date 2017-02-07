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
data class NodeSource(val node: Node): Source

private var nextId = 0

internal fun freshNodeId() = nextId++

interface TypeNode : Node {
    interface Visitor<T> {
        fun visit(node: TypeReferenceNode): T
    }

    fun <T> accept(visitor: Visitor<T>): T
}

data class TypeReferenceNode(
    override val name: String,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ReferenceNode, TypeNode {
    override val children: List<Node>
        get() = listOf()

    override fun <T> accept(visitor: TypeNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class ModuleNode(
    val name: String,
    val body: List<ModuleStatementNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : Node {
    override val children: List<Node>
        get() = body
}

interface ModuleStatementNode: Node {
    interface Visitor<T> {
        fun visit(node: ShapeNode): T
        fun visit(node: FunctionNode): T
    }

    fun <T> accept(visitor: Visitor<T>): T
}

data class ShapeNode(
    override val name: String,
    val fields: List<ShapeFieldNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): VariableBindingNode, ModuleStatementNode {
    override val children: List<Node>
        get() = fields

    override fun <T> accept(visitor: ModuleStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class ShapeFieldNode(
    val name: String,
    val type: TypeNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): Node {
    override val children: List<Node>
        get() = listOf(type)
}

data class FunctionNode(
    override val name: String,
    val arguments: List<ArgumentNode>,
    val returnType: TypeNode,
    val body: List<StatementNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : VariableBindingNode, ModuleStatementNode {
    override val children: List<Node>
        get() = arguments + returnType + body

    override fun <T> accept(visitor: ModuleStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class ArgumentNode(
    override val name: String,
    val type: TypeNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : VariableBindingNode, Node {
    override val children: List<Node>
        get() = listOf(type)
}

interface StatementNode : Node {
    interface Visitor<T> {
        fun visit(node: BadStatementNode): T {
            throw UnsupportedOperationException("not implemented")
        }
        fun visit(node: ReturnNode): T
        fun visit(node: IfStatementNode): T
        fun visit(node: ExpressionStatementNode): T
        fun visit(node: ValNode): T
    }

    fun <T> accept(visitor: StatementNode.Visitor<T>): T
}

data class BadStatementNode(
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : StatementNode {
    override val children: List<Node>
        get() = listOf()

    override fun <T> accept(visitor: StatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class ReturnNode(
    val expression: ExpressionNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : StatementNode {
    override val children: List<Node>
        get() = listOf(expression)

    override fun <T> accept(visitor: StatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class IfStatementNode(
    val condition: ExpressionNode,
    val trueBranch: List<StatementNode>,
    val falseBranch: List<StatementNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : StatementNode {
    override val children: List<Node>
        get() = listOf(condition) + trueBranch + falseBranch

    override fun <T> accept(visitor: StatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class ExpressionStatementNode(
    val expression: ExpressionNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): StatementNode {
    override val children: List<Node>
        get() = listOf(expression)

    override fun <T> accept(visitor: StatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class ValNode(
    override val name: String,
    val expression: ExpressionNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): VariableBindingNode, StatementNode {
    override val children: List<Node>
        get() = listOf(expression)

    override fun <T> accept(visitor: StatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

interface ExpressionNode : Node {
    interface Visitor<T> {
        fun visit(node: BooleanLiteralNode): T
        fun visit(node: IntegerLiteralNode): T
        fun visit(node: StringLiteralNode): T
        fun visit(node: VariableReferenceNode): T
        fun visit(node: BinaryOperationNode): T
        fun visit(node: FunctionCallNode): T
        fun visit(node: FieldAccessNode): T
    }

    fun <T> accept(visitor: ExpressionNode.Visitor<T>): T
}

data class BooleanLiteralNode(
    val value: Boolean,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): ExpressionNode {
    override val children: List<Node>
        get() = listOf()

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class IntegerLiteralNode(
    val value: Int,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ExpressionNode {
    override val children: List<Node>
        get() = listOf()

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class StringLiteralNode(
    val value: String,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ExpressionNode {
    override val children: List<Node>
        get() = listOf()

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class VariableReferenceNode(
    override val name: String,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ReferenceNode, ExpressionNode {
    override val children: List<Node>
        get() = listOf()

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class BinaryOperationNode(
    val operator: Operator,
    val left: ExpressionNode,
    val right: ExpressionNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ExpressionNode {
    override val children: List<Node>
        get() = listOf(left, right)

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class FunctionCallNode(
    val function: ExpressionNode,
    val positionalArguments: List<ExpressionNode>,
    val namedArguments: Map<String, ExpressionNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ExpressionNode {
    override val children: List<Node>
        get() = listOf(function) + positionalArguments + namedArguments.values

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class FieldAccessNode(
    val receiver: ExpressionNode,
    val fieldName: String,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ExpressionNode {
    override val children: List<Node>
        get() = listOf(receiver)

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

enum class Operator {
    EQUALS,
    ADD,
    SUBTRACT,
    MULTIPLY
}
