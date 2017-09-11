package org.shedlang.compiler.ast

import org.shedlang.compiler.nullableToList
import org.shedlang.compiler.types.Variance

interface Node {
    val source: Source
    val nodeId: Int
    val children: List<Node>
}

interface VariableBindingNode: Node {
    val name: String
}

interface TypeDeclarationNode: VariableBindingNode

interface ReferenceNode: Node {
    val name: String
}

interface Source {
    fun describe(): String
}

data class StringSource(
    val filename: String,
    val contents: String,
    val characterIndex: Int
) : Source {
    override fun describe(): String {
        val lines = contents.splitToSequence("\n")
        var position = 0

        for ((lineIndex, line) in lines.withIndex()) {
            val nextLinePosition = position + line.length + 1
            if (nextLinePosition > characterIndex || nextLinePosition >= contents.length) {
                return context(
                    line,
                    lineIndex = lineIndex,
                    columnIndex = characterIndex - position
                )
            }
            position = nextLinePosition
        }
        throw Exception("should be impossible (but evidently isn't)")
    }

    private fun context(line: String, lineIndex: Int, columnIndex: Int): String {
        return "${filename}:${lineIndex + 1}:${columnIndex + 1}\n${line}\n${" ".repeat(columnIndex)}^"
    }
}
data class NodeSource(val node: Node): Source {
    override fun describe(): String {
        return node.source.describe()
    }
}

private var nextId = 0

internal fun freshNodeId() = nextId++

interface StaticNode : Node {
    interface Visitor<T> {
        fun visit(node: StaticReferenceNode): T
        fun visit(node: StaticFieldAccessNode): T
        fun visit(node: StaticApplicationNode): T
        fun visit(node: FunctionTypeNode): T
    }

    fun <T> accept(visitor: Visitor<T>): T
}

data class StaticReferenceNode(
    override val name: String,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ReferenceNode, StaticNode {
    override val children: List<Node>
        get() = listOf()

    override fun <T> accept(visitor: StaticNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class StaticFieldAccessNode(
    val receiver: StaticNode,
    val fieldName: String,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : StaticNode {
    override val children: List<Node>
        get() = listOf(receiver)

    override fun <T> accept(visitor: StaticNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class StaticApplicationNode(
    val receiver: StaticNode,
    val arguments: List<StaticNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : StaticNode {
    override val children: List<Node>
        get() = listOf(receiver) + arguments

    override fun <T> accept(visitor: StaticNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class FunctionTypeNode(
    val staticParameters: List<StaticParameterNode>,
    val arguments: List<StaticNode>,
    val returnType: StaticNode,
    val effects: List<StaticNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): StaticNode {
    override val children: List<Node>
        get() = staticParameters + arguments + effects + listOf(returnType)

    override fun <T> accept(visitor: StaticNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class ModuleNode(
    val imports: List<ImportNode>,
    val body: List<ModuleStatementNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : Node {
    override val children: List<Node>
        get() = imports + body
}

data class ImportPath(val base: ImportPathBase, val parts: List<String>) {
    companion object {
        fun absolute(parts: List<String>) = ImportPath(ImportPathBase.Absolute, parts)
        fun relative(parts: List<String>) = ImportPath(ImportPathBase.Relative, parts)
    }
}
sealed class ImportPathBase {
    object Absolute: ImportPathBase()
    object Relative: ImportPathBase()
}

data class ImportNode(
    val path: ImportPath,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : VariableBindingNode {
    override val name: String
        get() = path.parts.last()

    override val children: List<Node>
        get() = listOf()
}

interface ModuleStatementNode: Node {
    interface Visitor<T> {
        fun visit(node: ShapeNode): T
        fun visit(node: UnionNode): T
        fun visit(node: FunctionDeclarationNode): T
        fun visit(node: ValNode): T
    }

    fun <T> accept(visitor: Visitor<T>): T
}

data class ShapeNode(
    override val name: String,
    val typeParameters: List<TypeParameterNode>,
    val tag: StaticNode?,
    val fields: List<ShapeFieldNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): TypeDeclarationNode, ModuleStatementNode {
    override val children: List<Node>
        get() = typeParameters + tag.nullableToList() + fields

    override fun <T> accept(visitor: ModuleStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class ShapeFieldNode(
    val name: String,
    val type: StaticNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): Node {
    override val children: List<Node>
        get() = listOf(type)
}

data class UnionNode(
    override val name: String,
    val typeParameters: List<TypeParameterNode>,
    val tag: Boolean,
    val members: List<StaticNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): TypeDeclarationNode, ModuleStatementNode {
    override val children: List<Node>
        get() = typeParameters + members

    override fun <T> accept(visitor: ModuleStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

interface FunctionNode : Node {
    val staticParameters: List<StaticParameterNode>
    val arguments: List<ArgumentNode>
    val returnType: StaticNode?
    val effects: List<StaticNode>
    val body: FunctionBody
}

sealed class FunctionBody {
    abstract val nodes: List<Node>
    abstract val statements: List<StatementNode>

    data class Statements(override val nodes: List<StatementNode>): FunctionBody() {
        override val statements: List<StatementNode>
            get() = nodes
    }
    data class Expression(val expression: ExpressionNode): FunctionBody() {
        override val nodes: List<Node>
            get() = listOf(expression)

        override val statements: List<StatementNode>
            get() = listOf(ReturnNode(expression, source = expression.source))
    }
}

data class FunctionExpressionNode(
    override val staticParameters: List<StaticParameterNode>,
    override val arguments: List<ArgumentNode>,
    override val returnType: StaticNode?,
    override val effects: List<StaticNode>,
    override val body: FunctionBody,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : FunctionNode, ExpressionNode {
    override val children: List<Node>
        get() = arguments + effects + listOfNotNull(returnType) + body.nodes

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class FunctionDeclarationNode(
    override val name: String,
    override val staticParameters: List<StaticParameterNode>,
    override val arguments: List<ArgumentNode>,
    override val returnType: StaticNode,
    override val effects: List<StaticNode>,
    override val body: FunctionBody.Statements,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : FunctionNode, VariableBindingNode, ModuleStatementNode {
    override val children: List<Node>
        get() = arguments + effects + returnType + body.nodes

    val bodyStatements: List<StatementNode>
        get() = body.nodes

    override fun <T> accept(visitor: ModuleStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

interface StaticParameterNode: VariableBindingNode {
    fun <T> accept(visitor: Visitor<T>): T

    interface Visitor<T> {
        fun visit(node: TypeParameterNode): T
        fun visit(node: EffectParameterNode): T
    }
}

data class TypeParameterNode(
    override val name: String,
    val variance: Variance,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): StaticParameterNode, Node {
    override val children: List<Node>
        get() = listOf()

    override fun <T> accept(visitor: StaticParameterNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class EffectParameterNode(
    override val name: String,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): StaticParameterNode, Node {
    override val children: List<Node>
        get() = listOf()

    override fun <T> accept(visitor: StaticParameterNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class ArgumentNode(
    override val name: String,
    val type: StaticNode,
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
): VariableBindingNode, StatementNode, ModuleStatementNode {
    override val children: List<Node>
        get() = listOf(expression)

    override fun <T> accept(visitor: StatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
    override fun <T> accept(visitor: ModuleStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

interface ExpressionNode : Node {
    interface Visitor<T> {
        fun visit(node: UnitLiteralNode): T
        fun visit(node: BooleanLiteralNode): T
        fun visit(node: IntegerLiteralNode): T
        fun visit(node: StringLiteralNode): T
        fun visit(node: VariableReferenceNode): T
        fun visit(node: BinaryOperationNode): T
        fun visit(node: IsNode): T
        fun visit(node: CallNode): T
        fun visit(node: FieldAccessNode): T
        fun visit(node: FunctionExpressionNode): T
    }

    fun <T> accept(visitor: ExpressionNode.Visitor<T>): T
}

data class UnitLiteralNode(
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): ExpressionNode {
    override val children: List<Node>
        get() = listOf()

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
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

data class IsNode(
    val expression: ExpressionNode,
    val type: StaticNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ExpressionNode {
    override val children: List<Node>
        get() = listOf(expression, type)

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class CallNode(
    val receiver: ExpressionNode,
    val staticArguments: List<StaticNode>,
    val positionalArguments: List<ExpressionNode>,
    val namedArguments: List<CallNamedArgumentNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ExpressionNode {
    override val children: List<Node>
        get() = listOf(receiver) + staticArguments + positionalArguments + namedArguments

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class CallNamedArgumentNode(
    val name: String,
    val expression: ExpressionNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): Node {
    override val children: List<Node>
        get() = listOf(expression)
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
