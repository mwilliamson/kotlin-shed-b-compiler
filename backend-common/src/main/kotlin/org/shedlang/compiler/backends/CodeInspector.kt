package org.shedlang.compiler.backends

import org.shedlang.compiler.Module
import org.shedlang.compiler.ast.CallNode

interface CodeInspector {
    fun isCast(node: CallNode): Boolean
}

class ModuleCodeInspector(private val module: Module.Shed): CodeInspector {
    override fun isCast(node: CallNode): Boolean {
        return org.shedlang.compiler.isCast(node, references = module.references)
    }
}

class FakeCodeInspector: CodeInspector {
    override fun isCast(node: CallNode): Boolean {
        return false
    }
}
