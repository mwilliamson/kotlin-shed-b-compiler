package org.shedlang.compiler.ast

interface Node {

}

data class ModuleNode(val name: String) : Node
