package org.shedlang.compiler.ast

import org.shedlang.compiler.nullableToList
import org.shedlang.compiler.types.*
import java.math.BigInteger

interface Node {
    val source: Source
    val nodeId: Int
    val children: List<Node>
}

data class Identifier(val value: String) : Comparable<Identifier> {
    override fun compareTo(other: Identifier): Int {
        return value.compareTo(other.value)
    }
}

fun Node.descendants(): List<Node> {
    return this.children.flatMap { child ->
        listOf(child) + child.descendants()
    }
}

interface VariableBindingNode: Node {
    val name: Identifier
}

data class BuiltinVariable(
    override val name: Identifier,
    val type: Type,
    override val nodeId: Int = freshNodeId()
): VariableBindingNode {
    override val source: Source
        get() = BuiltinSource
    override val children: List<Node>
        get() = listOf()
}

fun builtinType(name: String, type: Type) = BuiltinVariable(
    name = Identifier(name),
    type = MetaType(type)
)

fun builtinEffect(name: String, effect: Effect) = BuiltinVariable(
    name = Identifier(name),
    type = EffectType(effect)
)

fun builtinVariable(name: String, type: Type) = BuiltinVariable(
    name = Identifier(name),
    type = type
)

interface TypeDeclarationNode: VariableBindingNode

interface ReferenceNode: Node {
    val name: Identifier
}

interface Source {
    fun describe(): String
}

object BuiltinSource : Source {
    override fun describe(): String {
        return "builtin"
    }
}

data class StringSource(
    val filename: String,
    val contents: String,
    val characterIndex: Int
) : Source {
    fun at(index: Int): StringSource {
        return StringSource(filename, contents, characterIndex + index)
    }

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

fun freshNodeId() = nextId++

interface StaticExpressionNode : Node {
    interface Visitor<T> {
        fun visit(node: StaticReferenceNode): T
        fun visit(node: StaticFieldAccessNode): T
        fun visit(node: StaticApplicationNode): T
        fun visit(node: FunctionTypeNode): T
        fun visit(node: TupleTypeNode): T
    }

    fun <T> accept(visitor: Visitor<T>): T
}

data class StaticReferenceNode(
    override val name: Identifier,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ReferenceNode, StaticExpressionNode {
    override val children: List<Node>
        get() = listOf()

    override fun <T> accept(visitor: StaticExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class StaticFieldAccessNode(
    val receiver: StaticExpressionNode,
    val fieldName: Identifier,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : StaticExpressionNode {
    override val children: List<Node>
        get() = listOf(receiver)

    override fun <T> accept(visitor: StaticExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class StaticApplicationNode(
    val receiver: StaticExpressionNode,
    val arguments: List<StaticExpressionNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : StaticExpressionNode {
    override val children: List<Node>
        get() = listOf(receiver) + arguments

    override fun <T> accept(visitor: StaticExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class FunctionTypeNode(
    val staticParameters: List<StaticParameterNode>,
    val positionalParameters: List<StaticExpressionNode>,
    val namedParameters: List<ParameterNode>,
    val returnType: StaticExpressionNode,
    val effects: List<StaticExpressionNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): StaticExpressionNode {
    override val children: List<Node>
        get() = staticParameters + positionalParameters + namedParameters + effects + listOf(returnType)

    override fun <T> accept(visitor: StaticExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class TupleTypeNode(
    val elementTypes: List<StaticExpressionNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): StaticExpressionNode {
    override val children: List<Node>
        get() = elementTypes

    override fun <T> accept(visitor: StaticExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }

}

data class ModuleNode(
    val exports: List<ExportNode>,
    val imports: List<ImportNode>,
    val body: List<ModuleStatementNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : Node {
    override val children: List<Node>
        get() = imports + body

    val variableBinders: List<VariableBindingNode>
        get() = body.flatMap { statement -> statement.variableBinders() }
}

data class TypesModuleNode(
    val imports: List<ImportNode>,
    val body: List<ValTypeNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): Node {
    override val children: List<Node>
        get() = imports + body
}

data class ValTypeNode(
    override val name: Identifier,
    val type: StaticExpressionNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): VariableBindingNode {
    override val children: List<Node>
        get() = listOf(type)
}

data class ExportNode(
    override val name: Identifier,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ReferenceNode {
    override val children: List<Node>
        get() = listOf()
}

data class ImportPath(val base: ImportPathBase, val parts: List<Identifier>) {
    companion object {
        fun absolute(parts: List<String>) = ImportPath(ImportPathBase.Absolute, parts.map(::Identifier))
        fun relative(parts: List<String>) = ImportPath(ImportPathBase.Relative, parts.map(::Identifier))
    }
}
sealed class ImportPathBase {
    object Absolute: ImportPathBase()
    object Relative: ImportPathBase()
}

data class ImportNode(
    val target: TargetNode,
    val path: ImportPath,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : Node {
    override val children: List<Node>
        get() = listOf(target)
}

interface StatementNode: Node {
    fun variableBinders(): List<VariableBindingNode>
}

interface ModuleStatementNode: StatementNode {
    interface Visitor<T> {
        fun visit(node: TypeAliasNode): T
        fun visit(node: ShapeNode): T
        fun visit(node: UnionNode): T
        fun visit(node: FunctionDeclarationNode): T
        fun visit(node: ValNode): T
    }

    fun <T> accept(visitor: Visitor<T>): T
}

data class TypeAliasNode(
    override val name: Identifier,
    val expression: StaticExpressionNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): TypeDeclarationNode, ModuleStatementNode {
    override val children: List<Node>
        get() = listOf(expression)

    override fun <T> accept(visitor: ModuleStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }

    override fun variableBinders(): List<VariableBindingNode> {
        return listOf(this)
    }
}

data class ShapeNode(
    override val name: Identifier,
    override val staticParameters: List<StaticParameterNode>,
    override val extends: List<StaticExpressionNode>,
    override val fields: List<ShapeFieldNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): ShapeBaseNode, ModuleStatementNode {
    override val children: List<Node>
        get() = staticParameters + extends + fields

    override fun <T> accept(visitor: ModuleStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }

    override fun variableBinders(): List<VariableBindingNode> {
        return listOf(this)
    }
}

interface ShapeBaseNode: TypeDeclarationNode {
    val extends: List<StaticExpressionNode>
    val fields: List<ShapeFieldNode>
    val staticParameters: List<StaticParameterNode>
}

data class ShapeFieldNode(
    val shape: StaticExpressionNode?,
    val name: Identifier,
    val type: StaticExpressionNode?,
    val value: ExpressionNode?,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): Node {
    override val children: List<Node>
        get() = shape.nullableToList() + type.nullableToList() + value.nullableToList()
}

data class UnionNode(
    override val name: Identifier,
    val staticParameters: List<StaticParameterNode>,
    val superType: StaticReferenceNode?,
    val members: List<UnionMemberNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): TypeDeclarationNode, ModuleStatementNode {
    override val children: List<Node>
        get() = staticParameters + superType.nullableToList() + members

    override fun <T> accept(visitor: ModuleStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }

    override fun variableBinders(): List<VariableBindingNode> {
        return listOf(this) + members
    }
}

data class UnionMemberNode(
    override val name: Identifier,
    override val staticParameters: List<StaticParameterNode>,
    override val extends: List<StaticExpressionNode>,
    override val fields: List<ShapeFieldNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): ShapeBaseNode {
    override val children: List<Node>
        get() = staticParameters + extends + fields
}

interface FunctionNode : Node {
    val staticParameters: List<StaticParameterNode>
    val parameters: List<ParameterNode>
    val namedParameters: List<ParameterNode>
    val returnType: StaticExpressionNode?
    val effects: List<StaticExpressionNode>
    val body: FunctionBody
}

sealed class FunctionBody {
    abstract val nodes: List<Node>
    abstract val statements: List<FunctionStatementNode>

    data class Statements(override val nodes: List<FunctionStatementNode>): FunctionBody() {
        override val statements: List<FunctionStatementNode>
            get() = nodes
    }
    data class Expression(val expression: ExpressionNode): FunctionBody() {
        override val nodes: List<Node>
            get() = listOf(expression)

        override val statements: List<FunctionStatementNode>
            get() = listOf(ExpressionStatementNode(expression, isReturn = true, source = expression.source))
    }
}

data class FunctionExpressionNode(
    override val staticParameters: List<StaticParameterNode>,
    override val parameters: List<ParameterNode>,
    override val namedParameters: List<ParameterNode>,
    override val returnType: StaticExpressionNode?,
    override val effects: List<StaticExpressionNode>,
    override val body: FunctionBody,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : FunctionNode, ExpressionNode {
    override val children: List<Node>
        get() = parameters + namedParameters + effects + listOfNotNull(returnType) + body.nodes

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class FunctionDeclarationNode(
    override val name: Identifier,
    override val staticParameters: List<StaticParameterNode>,
    override val parameters: List<ParameterNode>,
    override val namedParameters: List<ParameterNode>,
    override val returnType: StaticExpressionNode,
    override val effects: List<StaticExpressionNode>,
    override val body: FunctionBody.Statements,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : FunctionNode, VariableBindingNode, ModuleStatementNode, FunctionStatementNode {
    override val isReturn: Boolean
        get() = false

    override val children: List<Node>
        get() = parameters + namedParameters + effects + returnType + body.nodes

    val bodyStatements: List<FunctionStatementNode>
        get() = body.nodes

    override fun <T> accept(visitor: ModuleStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }

    override fun <T> accept(visitor: FunctionStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }

    override fun variableBinders(): List<VariableBindingNode> {
        return listOf(this)
    }
}

interface StaticParameterNode: VariableBindingNode {
    fun <T> accept(visitor: Visitor<T>): T
    fun copy(): StaticParameterNode

    interface Visitor<T> {
        fun visit(node: TypeParameterNode): T
        fun visit(node: EffectParameterNode): T
    }
}

data class TypeParameterNode(
    override val name: Identifier,
    val variance: Variance,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): StaticParameterNode, Node {
    override val children: List<Node>
        get() = listOf()

    override fun <T> accept(visitor: StaticParameterNode.Visitor<T>): T {
        return visitor.visit(this)
    }

    override fun copy(): TypeParameterNode {
        return copy(nodeId = freshNodeId())
    }
}

data class EffectParameterNode(
    override val name: Identifier,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): StaticParameterNode, Node {
    override val children: List<Node>
        get() = listOf()

    override fun <T> accept(visitor: StaticParameterNode.Visitor<T>): T {
        return visitor.visit(this)
    }

    override fun copy(): EffectParameterNode {
        return copy(nodeId = freshNodeId())
    }
}

data class ParameterNode(
    override val name: Identifier,
    val type: StaticExpressionNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : VariableBindingNode, Node {
    override val children: List<Node>
        get() = listOf(type)
}

interface FunctionStatementNode : StatementNode {
    interface Visitor<T> {
        fun visit(node: BadStatementNode): T {
            throw UnsupportedOperationException("not implemented")
        }
        fun visit(node: ExpressionStatementNode): T
        fun visit(node: ValNode): T
        fun visit(node: FunctionDeclarationNode): T
    }

    val isReturn: Boolean
    fun <T> accept(visitor: FunctionStatementNode.Visitor<T>): T
}

data class BadStatementNode(
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : FunctionStatementNode {
    override val children: List<Node>
        get() = listOf()

    override val isReturn: Boolean
        get() = false

    override fun <T> accept(visitor: FunctionStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }

    override fun variableBinders(): List<VariableBindingNode> {
        return listOf()
    }
}

data class IfNode(
    val conditionalBranches: List<ConditionalBranchNode>,
    val elseBranch: List<FunctionStatementNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ExpressionNode {
    override val children: List<Node>
        get() = conditionalBranches + elseBranch

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }

    val branchBodies: Iterable<List<FunctionStatementNode>>
        get() = conditionalBranches.map { branch -> branch.body } + listOf(elseBranch)
}

data class ConditionalBranchNode(
    val condition: ExpressionNode,
    val body: List<FunctionStatementNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : Node {
    override val children: List<Node>
        get() = listOf(condition) + body
}

data class WhenNode(
    val expression: ExpressionNode,
    val branches: List<WhenBranchNode>,
    val elseBranch: List<FunctionStatementNode>?,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ExpressionNode {
    override val children: List<Node>
        get() = listOf(expression) + branches + elseBranch.orEmpty()

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class WhenBranchNode(
    val type: StaticExpressionNode,
    val body: List<FunctionStatementNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : Node {
    override val children: List<Node>
        get() = listOf(type) + body
}

data class ExpressionStatementNode(
    val expression: ExpressionNode,
    override val isReturn: Boolean,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): FunctionStatementNode {
    override val children: List<Node>
        get() = listOf(expression)

    override fun <T> accept(visitor: FunctionStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }

    override fun variableBinders(): List<VariableBindingNode> {
        return listOf()
    }
}

data class ValNode(
    val target: TargetNode,
    val expression: ExpressionNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): FunctionStatementNode, ModuleStatementNode {
    override val children: List<Node>
        get() = listOf(target, expression)

    override val isReturn: Boolean
        get() = false

    override fun <T> accept(visitor: FunctionStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
    override fun <T> accept(visitor: ModuleStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }

    override fun variableBinders(): List<VariableBindingNode> {
        return target.variableBinders()
    }
}

sealed class TargetNode: Node {
    abstract fun variableBinders(): List<VariableBindingNode>

    data class Variable(
        override val name: Identifier,
        override val source: Source,
        override val nodeId: Int = freshNodeId()
    ): VariableBindingNode, TargetNode() {
        override val children: List<Node>
            get() = listOf()

        override fun variableBinders(): List<VariableBindingNode> {
            return listOf(this)
        }
    }

    data class Tuple(
        val elements: List<TargetNode>,
        override val source: Source,
        override val nodeId: Int = freshNodeId()
    ): TargetNode() {
        override val children: List<Node>
            get() = elements

        override fun variableBinders(): List<VariableBindingNode> {
            return elements.flatMap { element -> element.variableBinders() }
        }
    }

    data class Fields(
        val fields: List<Pair<FieldNameNode, TargetNode>>,
        override val source: Source,
        override val nodeId: Int = freshNodeId()
    ): TargetNode() {
        override val children: List<Node>
            get() = fields.flatMap { (l, r) -> listOf(l, r) }

        override fun variableBinders(): List<VariableBindingNode> {
            return fields.flatMap { (_, target) -> target.variableBinders() }
        }
    }
}

interface ExpressionNode : Node {
    interface Visitor<T> {
        fun visit(node: UnitLiteralNode): T
        fun visit(node: BooleanLiteralNode): T
        fun visit(node: IntegerLiteralNode): T
        fun visit(node: StringLiteralNode): T
        fun visit(node: CodePointLiteralNode): T
        fun visit(node: SymbolNode): T
        fun visit(node: TupleNode): T
        fun visit(node: VariableReferenceNode): T
        fun visit(node: UnaryOperationNode): T
        fun visit(node: BinaryOperationNode): T
        fun visit(node: IsNode): T
        fun visit(node: CallNode): T
        fun visit(node: PartialCallNode): T
        fun visit(node: FieldAccessNode): T
        fun visit(node: FunctionExpressionNode): T
        fun visit(node: IfNode): T
        fun visit(node: WhenNode): T
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
    val value: BigInteger,
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

data class CodePointLiteralNode(
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

data class SymbolNode(
    val name: String,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ExpressionNode {
    override val children: List<Node>
        get() = listOf()

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class TupleNode(
    val elements: List<ExpressionNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): ExpressionNode {
    override val children: List<Node>
        get() = elements

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class VariableReferenceNode(
    override val name: Identifier,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ReferenceNode, ExpressionNode {
    override val children: List<Node>
        get() = listOf()

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class UnaryOperationNode(
    val operator: UnaryOperator,
    val operand: ExpressionNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ExpressionNode {
    override val children: List<Node>
        get() = listOf(operand)

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class BinaryOperationNode(
    val operator: BinaryOperator,
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
    val type: StaticExpressionNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ExpressionNode {
    override val children: List<Node>
        get() = listOf(expression, type)

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

interface CallBaseNode: Node {
    val receiver: ExpressionNode
    val staticArguments: List<StaticExpressionNode>
    val positionalArguments: List<ExpressionNode>
    val namedArguments: List<CallNamedArgumentNode>
}

data class CallNode(
    override val receiver: ExpressionNode,
    override val staticArguments: List<StaticExpressionNode>,
    override val positionalArguments: List<ExpressionNode>,
    override val namedArguments: List<CallNamedArgumentNode>,
    val hasEffect: Boolean,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : CallBaseNode, ExpressionNode {
    override val children: List<Node>
        get() = listOf(receiver) + staticArguments + positionalArguments + namedArguments

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class PartialCallNode(
    override val receiver: ExpressionNode,
    override val staticArguments: List<StaticExpressionNode>,
    override val positionalArguments: List<ExpressionNode>,
    override val namedArguments: List<CallNamedArgumentNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : CallBaseNode, ExpressionNode {
    override val children: List<Node>
        get() = listOf(receiver) + staticArguments + positionalArguments + namedArguments

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class CallNamedArgumentNode(
    val name: Identifier,
    val expression: ExpressionNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): Node {
    override val children: List<Node>
        get() = listOf(expression)
}

data class FieldAccessNode(
    val receiver: ExpressionNode,
    val fieldName: FieldNameNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ExpressionNode {
    override val children: List<Node>
        get() = listOf(receiver, fieldName)

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class FieldNameNode(
    val identifier: Identifier,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : Node {
    override val children: List<Node>
        get() = listOf()
}

enum class UnaryOperator {
    MINUS,
    NOT
}

enum class BinaryOperator {
    EQUALS,
    NOT_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    ADD,
    SUBTRACT,
    MULTIPLY,
    AND,
    OR
}
