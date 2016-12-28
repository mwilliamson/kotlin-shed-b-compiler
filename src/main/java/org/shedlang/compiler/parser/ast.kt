package org.shedlang.compiler.ast

interface Node {
    val location: SourceLocation
}

data class SourceLocation(val filename: String, val characterIndex: Int)

interface TypeNode : Node
data class TypeReferenceNode(
    val name: String,
    override val location: SourceLocation
) : TypeNode

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

interface StatementNode : Node

data class ReturnNode(
    val expression: ExpressionNode,
    override val location: SourceLocation
) : StatementNode

interface ExpressionNode : Node

data class IntegerLiteralNode(
    val value: Int,
    override val location: SourceLocation
) : ExpressionNode