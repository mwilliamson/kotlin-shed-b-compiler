package org.shedlang.compiler.ast

import org.shedlang.compiler.mapNullable
import org.shedlang.compiler.nullableToList
import org.shedlang.compiler.types.*
import java.math.BigInteger

interface Node {
    val source: Source
    val nodeId: Int
    val structure: List<NodeStructure>
}

sealed class NodeStructure {
    class TypeLevelEval(val node: Node): NodeStructure()
    class Eval(val node: Node): NodeStructure()
    class SubEnv(val structure: List<NodeStructure>): NodeStructure()
    class DeferInitialise(val binding: VariableBindingNode, val structure: NodeStructure): NodeStructure()
    class Initialise(val binding: VariableBindingNode): NodeStructure()
}

object NodeStructures {
    fun typeLevelEval(node: Node): NodeStructure {
        return NodeStructure.TypeLevelEval(node)
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
        is NodeStructure.TypeLevelEval -> listOf(structure.node)
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
    type = metaType(type)
)

fun builtinEffect(name: String, effect: Effect) = BuiltinVariable(
    name = Identifier(name),
    type = effectType(effect)
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

data class FileSource(val filename: String): Source {
    override fun describe(): String {
        return filename
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

interface TypeLevelExpressionNode : Node {
    interface Visitor<T> {
        fun visit(node: ReferenceNode): T
        fun visit(node: TypeLevelFieldAccessNode): T
        fun visit(node: TypeLevelApplicationNode): T
        fun visit(node: FunctionTypeNode): T
        fun visit(node: TupleTypeNode): T
        fun visit(node: TypeLevelUnionNode): T
    }

    fun <T> accept(visitor: Visitor<T>): T
}

data class TypeLevelFieldAccessNode(
    val receiver: TypeLevelExpressionNode,
    val fieldName: FieldNameNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : TypeLevelExpressionNode {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.typeLevelEval(receiver), NodeStructures.typeLevelEval(fieldName))

    override fun <T> accept(visitor: TypeLevelExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class TypeLevelApplicationNode(
    val receiver: TypeLevelExpressionNode,
    val arguments: List<TypeLevelExpressionNode>,
    override val source: Source,
    val operatorSource: Source,
    override val nodeId: Int = freshNodeId()
) : TypeLevelExpressionNode {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.typeLevelEval(receiver)) + arguments.map(NodeStructures::typeLevelEval)

    override fun <T> accept(visitor: TypeLevelExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class FunctionTypeNode(
    val typeLevelParameters: List<TypeLevelParameterNode>,
    val positionalParameters: List<TypeLevelExpressionNode>,
    val namedParameters: List<FunctionTypeNamedParameterNode>,
    val returnType: TypeLevelExpressionNode,
    val effect: TypeLevelExpressionNode?,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): TypeLevelExpressionNode {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.subEnv(
            (typeLevelParameters + positionalParameters + namedParameters + effect.nullableToList() + listOf(returnType)).map(NodeStructures::typeLevelEval)
        ))

    override fun <T> accept(visitor: TypeLevelExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class FunctionTypeNamedParameterNode(
    override val name: Identifier,
    val type: TypeLevelExpressionNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : VariableBindingNode, Node {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.typeLevelEval(type), NodeStructures.initialise(this))
}

data class TupleTypeNode(
    val elementTypes: List<TypeLevelExpressionNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): TypeLevelExpressionNode {
    override val structure: List<NodeStructure>
        get() = elementTypes.map(NodeStructures::typeLevelEval)

    override fun <T> accept(visitor: TypeLevelExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class TypeLevelUnionNode(
    val elements: List<TypeLevelExpressionNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): TypeLevelExpressionNode {
    override val structure: List<NodeStructure>
        get() = elements.map(NodeStructures::typeLevelEval)

    override fun <T> accept(visitor: TypeLevelExpressionNode.Visitor<T>): T {
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
        get() = listOf(NodeStructures.subEnv((imports + body).map(NodeStructures::typeLevelEval)))
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
    val type: TypeLevelExpressionNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): TypesModuleStatementNode, VariableBindingNode {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.typeLevelEval(type), NodeStructures.initialise(this))

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
): VariableBindingNode, FunctionStatementNode, ModuleStatementNode {
    override val structure: List<NodeStructure>
        get() = operations.map { operation -> NodeStructures.eval(operation) } + NodeStructures.initialise(this)

    override val terminatesBlock: Boolean
        get() = false

    override fun <T> accept(visitor: ModuleStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }

    override fun <T> accept(visitor: FunctionStatementNode.Visitor<T>): T {
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
        get() = listOf(NodeStructures.typeLevelEval(type))
}

data class TypeAliasNode(
    override val name: Identifier,
    val expression: TypeLevelExpressionNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): TypeDeclarationNode, ModuleStatementNode {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.typeLevelEval(expression), NodeStructures.initialise(this))

    override fun <T> accept(visitor: ModuleStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class ShapeNode(
    override val name: Identifier,
    override val typeLevelParameters: List<TypeLevelParameterNode>,
    override val extends: List<TypeLevelExpressionNode>,
    override val fields: List<ShapeFieldNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): ShapeBaseNode, FunctionStatementNode, ModuleStatementNode {
    override val structure: List<NodeStructure>
        get() = listOf(
            // TODO: switch static evaluation to be lazily resolved?
            NodeStructures.deferInitialise(this, NodeStructures.subEnv(
                (typeLevelParameters + extends).map(NodeStructures::typeLevelEval) + fields.map(NodeStructures::eval)
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
    val extends: List<TypeLevelExpressionNode>
    val fields: List<ShapeFieldNode>
    val typeLevelParameters: List<TypeLevelParameterNode>
}

data class ShapeFieldNode(
    val shape: TypeLevelExpressionNode?,
    val name: Identifier,
    val type: TypeLevelExpressionNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): Node {
    override val structure: List<NodeStructure>
        get() = (shape.nullableToList() + listOf(type)).map(NodeStructures::typeLevelEval)
}

data class UnionNode(
    override val name: Identifier,
    val typeLevelParameters: List<TypeLevelParameterNode>,
    val superType: ReferenceNode?,
    val members: List<UnionMemberNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): TypeDeclarationNode, ModuleStatementNode {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.subEnv(
            (typeLevelParameters + superType.nullableToList()).map(NodeStructures::typeLevelEval)
        )) + members.map(NodeStructures::typeLevelEval) + listOf(NodeStructures.initialise(this))

    override fun <T> accept(visitor: ModuleStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class UnionMemberNode(
    override val name: Identifier,
    override val typeLevelParameters: List<TypeLevelParameterNode>,
    override val extends: List<TypeLevelExpressionNode>,
    override val fields: List<ShapeFieldNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): ShapeBaseNode {
    override val structure: List<NodeStructure>
        get() = listOf(
            // TODO: switch static evaluation to be lazily resolved?
            NodeStructures.deferInitialise(this, NodeStructures.subEnv(
                (typeLevelParameters + extends).map(NodeStructures::typeLevelEval) + fields.map(NodeStructures::eval)
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
    val typeLevelParameters: List<TypeLevelParameterNode>
    val parameters: List<ParameterNode>
    val namedParameters: List<ParameterNode>
    val returnType: TypeLevelExpressionNode?
    val effect: FunctionEffectNode?
    val body: Block
    val inferReturnType: Boolean
}

sealed class FunctionEffectNode: Node {
    data class Infer(
        override val source: Source,
        override val nodeId: Int = freshNodeId(),
    ): FunctionEffectNode() {
        override val structure: List<NodeStructure>
            get() = listOf()
    }

    data class Explicit(
        val expression: TypeLevelExpressionNode,
        override val source: Source,
        override val nodeId: Int = freshNodeId(),
    ): FunctionEffectNode() {
        override val structure: List<NodeStructure>
            get() = listOf(NodeStructures.typeLevelEval(expression))
    }
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

    fun valueSource(): Source {
        val last = statements.lastOrNull()
        return if (last == null) {
            source
        } else {
            last.source
        }
    }
}

data class FunctionExpressionNode(
    override val typeLevelParameters: List<TypeLevelParameterNode>,
    override val parameters: List<ParameterNode>,
    override val namedParameters: List<ParameterNode>,
    override val returnType: TypeLevelExpressionNode?,
    override val effect: FunctionEffectNode?,
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
    override val typeLevelParameters: List<TypeLevelParameterNode>,
    override val parameters: List<ParameterNode>,
    override val namedParameters: List<ParameterNode>,
    override val returnType: TypeLevelExpressionNode,
    override val effect: FunctionEffectNode?,
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
        function.typeLevelParameters.map(NodeStructures::typeLevelEval) +
            (function.parameters + function.namedParameters + function.returnType.nullableToList() + function.effect.nullableToList() + listOf(function.body)).map(NodeStructures::eval)
    )
}

interface TypeLevelParameterNode: VariableBindingNode {
    fun <T> accept(visitor: Visitor<T>): T
    fun copy(): TypeLevelParameterNode

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
): TypeLevelParameterNode, Node {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.initialise(this))

    override fun <T> accept(visitor: TypeLevelParameterNode.Visitor<T>): T {
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
): TypeLevelParameterNode, Node {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.initialise(this))

    override fun <T> accept(visitor: TypeLevelParameterNode.Visitor<T>): T {
        return visitor.visit(this)
    }

    override fun copy(): EffectParameterNode {
        return copy(nodeId = freshNodeId())
    }
    }

    data class ParameterNode(
        override val name: Identifier,
        val type: TypeLevelExpressionNode?,
        override val source: Source,
        override val nodeId: Int = freshNodeId()
    ) : VariableBindingNode, Node {
        override val structure: List<NodeStructure>
            get() = type.nullableToList().map(NodeStructures::typeLevelEval) + listOf(NodeStructures.initialise(this))
    }

interface FunctionStatementNode : StatementNode {
    interface Visitor<T> {
        fun visit(node: BadStatementNode): T {
            throw UnsupportedOperationException("not implemented")
        }
        fun visit(node: ExpressionStatementNode): T
        fun visit(node: ResumeNode): T
        fun visit(node: ValNode): T
        fun visit(node: FunctionDefinitionNode): T
        fun visit(node: ShapeNode): T
        fun visit(node: EffectDefinitionNode): T
    }

    val terminatesBlock: Boolean
    fun <T> accept(visitor: FunctionStatementNode.Visitor<T>): T
}

data class BadStatementNode(
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : FunctionStatementNode {
    override val structure: List<NodeStructure>
        get() = listOf()

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
    val elseSource: Source,
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
    val type: TypeLevelExpressionNode,
    val target: TargetNode.Fields,
    val body: Block,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : Node {
    override val structure: List<NodeStructure>
        get() = listOf(
            NodeStructures.typeLevelEval(type),
            NodeStructures.subEnv(listOf(
                NodeStructures.eval(target),
                NodeStructures.eval(body),
            ))
        )
}

data class HandleNode(
    val effect: TypeLevelExpressionNode,
    val initialState: ExpressionNode?,
    val body: Block,
    val handlers: List<HandlerNode>,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): ExpressionNode {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.typeLevelEval(effect)) +
            initialState.mapNullable(NodeStructures::eval).nullableToList() +
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
    }

    override val terminatesBlock: Boolean
        get() = type != Type.NO_VALUE

    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.eval(expression))

    override fun <T> accept(visitor: FunctionStatementNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class ResumeNode(
    val expression: ExpressionNode,
    val newState: ExpressionNode?,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
): FunctionStatementNode {
    override val terminatesBlock: Boolean
        get() = true

    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.eval(expression)) + newState.mapNullable(NodeStructures::eval).nullableToList()

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

    data class Ignore(
        override val source: Source,
        override val nodeId: Int = freshNodeId()
    ): TargetNode() {
        override val structure: List<NodeStructure>
            get() = listOf()

        override fun variableBinders(): List<VariableBindingNode> {
            return listOf()
        }
    }

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
        fun visit(node: TypeLevelCallNode): T
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
) : ExpressionNode, TypeLevelExpressionNode {
    override val structure: List<NodeStructure>
        get() = listOf()

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }

    override fun <T> accept(visitor: TypeLevelExpressionNode.Visitor<T>): T {
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
    val type: TypeLevelExpressionNode,
    override val source: Source,
    override val nodeId: Int = freshNodeId()
) : ExpressionNode {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.eval(expression), NodeStructures.typeLevelEval(type))

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

interface CallBaseNode: ExpressionNode {
    val receiver: ExpressionNode
    val typeLevelArguments: List<TypeLevelExpressionNode>
    val positionalArguments: List<ExpressionNode>
    val fieldArguments: List<FieldArgumentNode>
    val operatorSource: Source
}

data class CallNode(
    override val receiver: ExpressionNode,
    override val typeLevelArguments: List<TypeLevelExpressionNode>,
    override val positionalArguments: List<ExpressionNode>,
    override val fieldArguments: List<FieldArgumentNode>,
    val hasEffect: Boolean,
    override val source: Source,
    override val operatorSource: Source,
    override val nodeId: Int = freshNodeId()
) : CallBaseNode, ExpressionNode {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.eval(receiver)) +
            typeLevelArguments.map(NodeStructures::typeLevelEval) +
            (positionalArguments + fieldArguments).map(NodeStructures::eval)

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

data class PartialCallNode(
    override val receiver: ExpressionNode,
    override val typeLevelArguments: List<TypeLevelExpressionNode>,
    override val positionalArguments: List<ExpressionNode>,
    override val fieldArguments: List<FieldArgumentNode>,
    override val source: Source,
    override val operatorSource: Source,
    override val nodeId: Int = freshNodeId()
) : CallBaseNode, ExpressionNode {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.eval(receiver)) +
            typeLevelArguments.map(NodeStructures::typeLevelEval) +
            (positionalArguments + fieldArguments).map(NodeStructures::eval)

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
}

sealed class FieldArgumentNode: Node {
    data class Named(
        val name: Identifier,
        val expression: ExpressionNode,
        override val source: Source,
        override val nodeId: Int = freshNodeId()
    ): FieldArgumentNode() {
        override val structure: List<NodeStructure>
            get() = listOf(NodeStructures.eval(expression))
    }

    data class Splat(
        val expression: ExpressionNode,
        override val source: Source,
        override val nodeId: Int = freshNodeId()
    ): FieldArgumentNode() {
        override val structure: List<NodeStructure>
            get() = listOf(NodeStructures.eval(expression))
    }
}

data class TypeLevelCallNode(
    val receiver: ExpressionNode,
    val arguments: List<TypeLevelExpressionNode>,
    override val source: Source,
    val operatorSource: Source,
    override val nodeId: Int = freshNodeId()
) : ExpressionNode {
    override val structure: List<NodeStructure>
        get() = listOf(NodeStructures.eval(receiver)) +
            arguments.map(NodeStructures::typeLevelEval)

    override fun <T> accept(visitor: ExpressionNode.Visitor<T>): T {
        return visitor.visit(this)
    }
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
    DIVIDE,
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
        override fun visit(node: TypeLevelCallNode): Iterable<Block>? = null
        override fun visit(node: FieldAccessNode): Iterable<Block>? = null
        override fun visit(node: FunctionExpressionNode): Iterable<Block>? = null
        override fun visit(node: IfNode): Iterable<Block>? = node.branchBodies
        override fun visit(node: WhenNode): Iterable<Block>? = node.branchBodies
        override fun visit(node: HandleNode): Iterable<Block>? = listOf(node.body)
    })
}
