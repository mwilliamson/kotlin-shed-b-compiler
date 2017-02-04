package org.shedlang.compiler.ast

internal fun descendantsAndSelf(node: Node): List<Node> {
    val result = mutableListOf<Node>()
    descendantsAndSelf(node, result)
    return result
}

private fun descendantsAndSelf(node: Node, collector: MutableCollection<Node>) {
    collector.add(node)
    descendants(node, collector)
}

private fun descendants(node: Node, collector: MutableCollection<Node>) {
    for (child in node.children) {
        descendantsAndSelf(child, collector)
    }
}
