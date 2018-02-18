package org.shedlang.compiler

import org.shedlang.compiler.ast.ReferenceNode
import org.shedlang.compiler.ast.VariableBindingNode

interface ResolvedReferences {
    operator fun get(node: ReferenceNode): VariableBindingNode
}
