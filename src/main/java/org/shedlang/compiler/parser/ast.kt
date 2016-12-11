package org.shedlang.compiler.ast

interface Node {

}

data class ModuleNode(val name: String, val body: List<FunctionNode>) : Node

data class FunctionNode(val name: String) : Node




