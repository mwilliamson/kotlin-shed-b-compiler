package org.shedlang.compiler.ast

import org.shedlang.compiler.nullableToList
import org.shedlang.compiler.types.Effect
import org.shedlang.compiler.types.StaticValueType
import org.shedlang.compiler.types.Type
import org.shedlang.compiler.types.Variance
import java.math.BigInteger

interface Node {
    val source: Source
    val nodeId: Int
    val structure: List<NodeStructure>
}

sealed class NodeStructure {
    class StaticEval(val node: Node): NodeStructure()
    class Eval(val node: Node): NodeStructure()
    class SubEnv(val structure: List<NodeStructure>): NodeStructure()
    class DeferInitialise(val binding: VariableBindingNode, val structure: NodeStructure): NodeStructure()
    class Initialise(val binding: VariableBindingNode): NodeStructure()
}

object NodeStructures {
    fun staticEval(node: Node): NodeStructure {
        return NodeStructure.StaticEval(node)
    }

    fun eval(node: Node): NodeStructure {
        return NodeStructure.Eval(node)
    }

    fun subEnv(structure: List<NodeStructure>): NodeStructure {
        return NodeStructure.SubEnv(structure)
    }

    fun deferInitialise(binding: VariableBindingNode, structure: NodeStructure): NodeStructure {
        return NodeStructure.DeferInitialise(binding, structure)
    }

    fun initialise(binding: VariableBindingNode): NodeStructure {
        return NodeStructure.Initialise(binding)
    }
}

data class Identifier(val value: String) : Comparable<Identifier> {
    override fun compareTo(other: Identifier): Int {
        return value.compareTo(other.value)
    }
}

typealias ModuleName = List<Identifier>

fun formatModuleName(moduleName: ModuleName): String {
    return moduleName.joinToString(".") { part -> part.value }
}

fun parseModuleName(moduleName: String): ModuleName {
    return moduleName.split(".").map(::Identifier)
}

fun Node.children(): List<Node> {
    return this.structure.flatMap(::structureToNodes)
}

fun structureToNodes(structure: NodeStructure): Iterable<Node> {
    return when (structure) {
        is NodeStructure.Eval -> listOf(structure.node)
        is NodeStructure.StaticEval -> listOf(structure.node)
        is NodeStructure.SubEnv -> structure.structure.flatMap(::structureToNodes)
        is NodeStructure.DeferInitialise -> structureToNodes(structure.structure)
        is NodeStructure.Initialise -> listOf()
    }
}

fun Node.descendants(): List<Node> {
    return this.children().flatMap { child ->
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

    override val structure: List<NodeStructure>
        get() = listOf()
}

fun builtinType(name: String, type: Type) = BuiltinVariable(
    name = Identifier(name),
    type = StaticValueType(type)
)

fun builtinEffect(name: String, effect: Effect) = BuiltinVariable(
    name = Identifier(name),
    type = StaticValueType(effect)
)

fun builtinVariable(name: String, type: Type) = BuiltinVariable(
    name = Identifier(name),
    type = type
)

interface TypeDeclarationNode: VariableBindingNode

interface Source {
    fun describe(): String
}

object NullSource : Source {
    override fun describe(): String {
        return "null"
    }
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
        fun visit(node: ReferenceNode): T
        fun visit(node: StaticFieldAccessNode): T
        fun visit(node: StaticApplicationNode): T
        fun visit(node: FunctionTypeNode): T
        fun visit(node: TupleTypeNode): T
        fun visit(node: StaticUnionNode): T
    }

    fun <T> accept(visitor: Visitor<T>): T
}

data class StaticFieldAccessNode(
    val receiver: StaticExpressionNode,
    val fieldName: FieldNameNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : StaticExpressionNode {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.staticEval(receiver), NodeStructures.staticEval(fieldName))

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
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.staticEval(receiver)) + arguments.map(NodeStructures::staticEval)

    override fun <T> accept(visitor: StaticExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class FunctionTypeNode(
    val staticParameters: List<StaticParameterNode>,
    val positionalParameters: List<StaticExpressionNode>,
    val namedParameters: List<ParameterNode>,
    val returnType: StaticExpressionNode,
    val effect: StaticExpressionNode?,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): StaticExpressionNode {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.subEnv(
            (staticParameters + positionalParameters + namedParameters + effect.nullableToList() + listOf(returnType)).map(NodeStructures::staticEval)
        ))


    override fun <T> accept(visitor: StaticExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class TupleTypeNode(
    val elementTypes: List<StaticExpressionNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): StaticExpressionNode {
    override val structure: List<NodeStructure>
        get() = elementTypes.map(NodeStructures::staticEval)

    override fun <T> accept(visitor: StaticExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class StaticUnionNode(
    val elements: List<StaticExpressionNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): StaticExpressionNode {
    override val structure: List<NodeStructure>
        get() = elements.map(NodeStructures::staticEval)

    override fun <T> accept(visitor: StaticExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class ModuleNode(
    val exports: List<ReferenceNode>,
    val imports: List<ImportNode>,
    val body: List<ModuleStatementNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : Node {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.subEnv((imports + body + exports).map(NodeStructures::eval)))
}

data class TypesModuleNode(
    val imports: List<ImportNode>,
    val body: List<TypesModuleStatementNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): Node {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.subEnv((imports + body).map(NodeStructures::staticEval)))
}

interface TypesModuleStatementNode: Node {
    interface Visitor<T> {
        fun visit(node: EffectDeclarationNode): T
        fun visit(node: ValTypeNode): T
    }

    fun <T> accept(visitor: Visitor<T>): T
}

data class EffectDeclarationNode(
    override val name: Identifier,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): TypesModuleStatementNode, VariableBindingNode {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.initialise(this))

    override fun <T> accept(visitor: TypesModuleStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class ValTypeNode(
    override val name: Identifier,
    val type: StaticExpressionNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): TypesModuleStatementNode, VariableBindingNode {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.staticEval(type), NodeStructures.initialise(this))

    override fun <T> accept(visitor: TypesModuleStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
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
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.eval(target))
}

interface StatementNode: Node

interface ModuleStatementNode: StatementNode {
    interface Visitor<T> {
        fun visit(node: EffectDefinitionNode): T
        fun visit(node: TypeAliasNode): T
        fun visit(node: ShapeNode): T
        fun visit(node: UnionNode): T
        fun visit(node: FunctionDefinitionNode): T
        fun visit(node: ValNode): T
        fun visit(node: VarargsDeclarationNode): T
    }

    fun <T> accept(visitor: Visitor<T>): T
}

data class EffectDefinitionNode(
    override val name: Identifier,
    val operations: List<OperationDefinitionNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): VariableBindingNode, ModuleStatementNode {
    override val structure: List<NodeStructure>
        get() = operations.map { operation -> NodeStructures.eval(operation) } + NodeStructures.initialise(this)

    override fun <T> accept(visitor: ModuleStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class OperationDefinitionNode(
    val name: Identifier,
    val type: FunctionTypeNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): Node {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.staticEval(type))
}

data class TypeAliasNode(
    override val name: Identifier,
    val expression: StaticExpressionNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): TypeDeclarationNode, ModuleStatementNode {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.staticEval(expression), NodeStructures.initialise(this))

    override fun <T> accept(visitor: ModuleStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class ShapeNode(
    override val name: Identifier,
    override val staticParameters: List<StaticParameterNode>,
    override val extends: List<StaticExpressionNode>,
    override val fields: List<ShapeFieldNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): ShapeBaseNode, FunctionStatementNode, ModuleStatementNode {
    override val structure: List<NodeStructure>
        get() = listOf(
            // TODO: switch static evaluation to be lazily resolved?
            NodeStructures.deferInitialise(this, NodeStructures.subEnv(
                (staticParameters + extends).map(NodeStructures::staticEval) + fields.map(NodeStructures::eval)
            ))
        )

    override val terminatesBlock: Boolean
        get() = false

    override fun <T> accept(visitor: FunctionStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }

    override fun <T> accept(visitor: ModuleStatementNode.Visitor<T>): T {
        return visitor.visit(this)
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
    override val structure: List<NodeStructure>
        get() = (shape.nullableToList() + type.nullableToList()).map(NodeStructures::staticEval) +
            value.nullableToList().map(NodeStructures::eval)
}

data class UnionNode(
    override val name: Identifier,
    val staticParameters: List<StaticParameterNode>,
    val superType: ReferenceNode?,
    val members: List<UnionMemberNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): TypeDeclarationNode, ModuleStatementNode {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.subEnv(
            (staticParameters + superType.nullableToList()).map(NodeStructures::staticEval)
        )) + members.map(NodeStructures::staticEval) + listOf(NodeStructures.initialise(this))

    override fun <T> accept(visitor: ModuleStatementNode.Visitor<T>): T {
        return visitor.visit(this)
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
    override val structure: List<NodeStructure>
        get() = listOf(
            // TODO: switch static evaluation to be lazily resolved?
            NodeStructures.deferInitialise(this, NodeStructures.subEnv(
                (staticParameters + extends).map(NodeStructures::staticEval) + fields.map(NodeStructures::eval)
            ))
        )
}

data class VarargsDeclarationNode(
    override val name: Identifier,
    val cons: ExpressionNode,
    val nil: ExpressionNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): ModuleStatementNode, VariableBindingNode {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.eval(cons), NodeStructures.eval(nil), NodeStructures.initialise(this))

    override fun <T> accept(visitor: ModuleStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

interface FunctionNode : Node {
    val staticParameters: List<StaticParameterNode>
    val parameters: List<ParameterNode>
    val namedParameters: List<ParameterNode>
    val returnType: StaticExpressionNode?
    val effect: StaticExpressionNode?
    val body: Block
    val inferReturnType: Boolean
}

data class Block(
    val statements: List<FunctionStatementNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): Node {
    override val structure: List<NodeStructure>
        get() = statements.map(NodeStructures::eval)

    val isTerminated: Boolean
        get() {
            val last = statements.lastOrNull()
            return last != null && last.terminatesBlock
        }
}

data class FunctionExpressionNode(
    override val staticParameters: List<StaticParameterNode>,
    override val parameters: List<ParameterNode>,
    override val namedParameters: List<ParameterNode>,
    override val returnType: StaticExpressionNode?,
    override val effect: StaticExpressionNode?,
    override val body: Block,
    override val inferReturnType: Boolean,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : FunctionNode, ExpressionNode {
    override val structure: List<NodeStructure>
        get() = listOf(functionSubEnv(this))

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class FunctionDefinitionNode(
    override val name: Identifier,
    override val staticParameters: List<StaticParameterNode>,
    override val parameters: List<ParameterNode>,
    override val namedParameters: List<ParameterNode>,
    override val returnType: StaticExpressionNode,
    override val effect: StaticExpressionNode?,
    override val body: Block,
    override val inferReturnType: Boolean,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : FunctionNode, VariableBindingNode, ModuleStatementNode, FunctionStatementNode {
    override val terminatesBlock: Boolean
        get() = false

    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.deferInitialise(this, functionSubEnv(this)))

    override fun <T> accept(visitor: ModuleStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }

    override fun <T> accept(visitor: FunctionStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

private fun functionSubEnv(function: FunctionNode): NodeStructure {
    return NodeStructures.subEnv(
        function.staticParameters.map(NodeStructures::staticEval) +
            (function.parameters + function.namedParameters + function.returnType.nullableToList() + function.effect.nullableToList() + listOf(function.body)).map(NodeStructures::eval)
    )
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
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.initialise(this))

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
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.initialise(this))

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
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.staticEval(type), NodeStructures.initialise(this))
}

interface FunctionStatementNode : StatementNode {
    interface Visitor<T> {
        fun visit(node: BadStatementNode): T {
            throw UnsupportedOperationException("not implemented")
        }
        fun visit(node: ExpressionStatementNode): T
        fun visit(node: ValNode): T
        fun visit(node: FunctionDefinitionNode): T
        fun visit(node: ShapeNode): T
    }

    val terminatesBlock: Boolean
    fun <T> accept(visitor: FunctionStatementNode.Visitor<T>): T
}

data class BadStatementNode(
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : FunctionStatementNode {
    override val structure: List<NodeStructure>
        get() = TODO("not implemented")

    override val terminatesBlock: Boolean
        get() = false

    override fun <T> accept(visitor: FunctionStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class IfNode(
    val conditionalBranches: List<ConditionalBranchNode>,
    val elseBranch: Block,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ExpressionNode {
    override val structure: List<NodeStructure>
        get() = conditionalBranches.map(NodeStructures::eval) + NodeStructures.subEnv(listOf(NodeStructures.eval(elseBranch)))

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }

    val branchBodies: Iterable<Block>
        get() = conditionalBranches.map { branch -> branch.body } + listOf(elseBranch)
}

data class ConditionalBranchNode(
    val condition: ExpressionNode,
    val body: Block,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : Node {
    override val structure: List<NodeStructure>
        get() = listOf(
            NodeStructures.eval(condition),
            NodeStructures.subEnv(listOf(NodeStructures.eval(body)))
        )
}

data class WhenNode(
    val expression: ExpressionNode,
    val conditionalBranches: List<WhenBranchNode>,
    val elseBranch: Block?,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ExpressionNode {
    override val structure: List<NodeStructure>
        get() = (listOf(expression) + conditionalBranches).map(NodeStructures::eval) +
            NodeStructures.subEnv(elseBranch.nullableToList().map(NodeStructures::eval))

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }

    val branchBodies: Iterable<Block>
        get() = conditionalBranches.map { branch -> branch.body } + elseBranch.nullableToList()
}

data class WhenBranchNode(
    val type: StaticExpressionNode,
    val body: Block,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : Node {
    override val structure: List<NodeStructure>
        get() = listOf(
            NodeStructures.staticEval(type),
            NodeStructures.subEnv(listOf(NodeStructures.eval(body)))
        )
}

data class HandleNode(
    val effect: StaticExpressionNode,
    val body: Block,
    val handlers: List<HandlerNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): ExpressionNode {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.staticEval(effect)) +
            handlers.map { handler -> NodeStructures.eval(handler) } +
            listOf(NodeStructures.subEnv(listOf(NodeStructures.eval(body))))

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class HandlerNode(
    val operationName: Identifier,
    val function: FunctionExpressionNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): Node {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.eval(function))
}

data class ExpressionStatementNode(
    val expression: ExpressionNode,
    val type: Type,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): FunctionStatementNode {
    enum class Type {
        NO_VALUE,

        VALUE,
        TAILREC,

        EXIT,
        RESUME
    }

    override val terminatesBlock: Boolean
        get() = type != Type.NO_VALUE

    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.eval(expression))

    override fun <T> accept(visitor: FunctionStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class ValNode(
    val target: TargetNode,
    val expression: ExpressionNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): FunctionStatementNode, ModuleStatementNode {
    override val structure: List<NodeStructure>
        get() = listOf(expression, target).map(NodeStructures::eval)

    override val terminatesBlock: Boolean
        get() = false

    override fun <T> accept(visitor: FunctionStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
    override fun <T> accept(visitor: ModuleStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

sealed class TargetNode: Node {
    abstract fun variableBinders(): List<VariableBindingNode>

    data class Variable(
        override val name: Identifier,
        override val source: Source,
        override val nodeId: Int = freshNodeId()
    ): VariableBindingNode, TargetNode() {
        override val structure: List<NodeStructure>
            get() = listOf(NodeStructures.initialise(this))

        override fun variableBinders(): List<VariableBindingNode> {
            return listOf(this)
        }
    }

    data class Tuple(
        val elements: List<TargetNode>,
        override val source: Source,
        override val nodeId: Int = freshNodeId()
    ): TargetNode() {
        override val structure: List<NodeStructure>
            get() = elements.map(NodeStructures::eval)

        override fun variableBinders(): List<VariableBindingNode> {
            return elements.flatMap { element -> element.variableBinders() }
        }
    }

    data class Fields(
        val fields: List<Pair<FieldNameNode, TargetNode>>,
        override val source: Source,
        override val nodeId: Int = freshNodeId()
    ): TargetNode() {
        override val structure: List<NodeStructure>
            get() = fields.flatMap { (fieldName, target) ->
                listOf(NodeStructures.eval(fieldName), NodeStructures.eval(target))
            }

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
        fun visit(node: UnicodeScalarLiteralNode): T
        fun visit(node: TupleNode): T
        fun visit(node: ReferenceNode): T
        fun visit(node: UnaryOperationNode): T
        fun visit(node: BinaryOperationNode): T
        fun visit(node: IsNode): T
        fun visit(node: CallNode): T
        fun visit(node: PartialCallNode): T
        fun visit(node: FieldAccessNode): T
        fun visit(node: FunctionExpressionNode): T
        fun visit(node: IfNode): T
        fun visit(node: WhenNode): T
        fun visit(node: HandleNode): T
    }

    fun <T> accept(visitor: ExpressionNode.Visitor<T>): T
}

data class UnitLiteralNode(
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): ExpressionNode {
    override val structure: List<NodeStructure>
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
    override val structure: List<NodeStructure>
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
    override val structure: List<NodeStructure>
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
    override val structure: List<NodeStructure>
        get() = listOf()

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class UnicodeScalarLiteralNode(
    val value: Int,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ExpressionNode {
    override val structure: List<NodeStructure>
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
    override val structure: List<NodeStructure>
        get() = elements.map(NodeStructures::eval)

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class ReferenceNode(
    val name: Identifier,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ExpressionNode, StaticExpressionNode {
    override val structure: List<NodeStructure>
        get() = listOf()

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }

    override fun <T> accept(visitor: StaticExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class UnaryOperationNode(
    val operator: UnaryOperator,
    val operand: ExpressionNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ExpressionNode {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.eval(operand))

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
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.eval(left), NodeStructures.eval(right))

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
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.eval(expression), NodeStructures.staticEval(type))

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
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.eval(receiver)) +
            staticArguments.map(NodeStructures::staticEval) +
            (positionalArguments + namedArguments).map(NodeStructures::eval)

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
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.eval(receiver)) +
            staticArguments.map(NodeStructures::staticEval) +
            (positionalArguments + namedArguments).map(NodeStructures::eval)

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
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.eval(expression))
}

data class FieldAccessNode(
    val receiver: ExpressionNode,
    val fieldName: FieldNameNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ExpressionNode {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.eval(receiver), NodeStructures.eval(fieldName))

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class FieldNameNode(
    val identifier: Identifier,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : Node {
    override val structure: List<NodeStructure>
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

fun expressionBranches(expression: ExpressionNode): Iterable<Block>? {
    return expression.accept(object : ExpressionNode.Visitor<Iterable<Block>?> {
        override fun visit(node: UnitLiteralNode): Iterable<Block>? = null
        override fun visit(node: BooleanLiteralNode): Iterable<Block>? = null
        override fun visit(node: IntegerLiteralNode): Iterable<Block>? = null
        override fun visit(node: StringLiteralNode): Iterable<Block>? = null
        override fun visit(node: UnicodeScalarLiteralNode): Iterable<Block>? = null
        override fun visit(node: TupleNode): Iterable<Block>? = null
        override fun visit(node: ReferenceNode): Iterable<Block>? = null
        override fun visit(node: UnaryOperationNode): Iterable<Block>? = null
        override fun visit(node: BinaryOperationNode): Iterable<Block>? = null
        override fun visit(node: IsNode): Iterable<Block>? = null
        override fun visit(node: CallNode): Iterable<Block>? = null
        override fun visit(node: PartialCallNode): Iterable<Block>? = null
        override fun visit(node: FieldAccessNode): Iterable<Block>? = null
        override fun visit(node: FunctionExpressionNode): Iterable<Block>? = null
        override fun visit(node: IfNode): Iterable<Block>? = node.branchBodies
        override fun visit(node: WhenNode): Iterable<Block>? = node.branchBodies
        override fun visit(node: HandleNode): Iterable<Block>? = listOf(node.body)
    })
}
