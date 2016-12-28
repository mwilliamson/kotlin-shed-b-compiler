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
    val returnType: TypeNode,
    override val location: SourceLocation
) : Node
