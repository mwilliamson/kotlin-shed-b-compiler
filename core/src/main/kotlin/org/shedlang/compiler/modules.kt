package org.shedlang.compiler

import org.shedlang.compiler.ast.ExpressionNode
import org.shedlang.compiler.ast.FunctionDeclarationNode
import org.shedlang.compiler.ast.ModuleNode
import org.shedlang.compiler.types.ModuleType
import org.shedlang.compiler.types.Type
import java.nio.file.Path
import java.nio.file.Paths

class ModuleSet(val modules: Collection<Module>)

sealed class Module {
    abstract val name: List<String>
    abstract val type: ModuleType

    class Shed(
        override val name: List<String>,
        val node: ModuleNode,
        override val type: ModuleType,
        val expressionTypes: ExpressionTypes,
        val references: ResolvedReferences
    ): Module() {
        fun hasMain() = node.body.any({ node ->
            node is FunctionDeclarationNode && node.name == "main"
        })
    }

    class Native(
        override val name: List<String>,
        override val type: ModuleType,
        private val filePath: Path
    ): Module() {
        fun platformPath(extension: String): Path {
            // TODO: avoid converting to String
            // TODO: remove duplication of extension
            return Paths.get(filePath.toString().removeSuffix(".types.shed") + extension)
        }
    }
}


interface ExpressionTypes {
    fun typeOf(node: ExpressionNode): Type
}

val emptyExpressionTypes: ExpressionTypes = ExpressionTypesMap(mapOf())

class ExpressionTypesMap(private val types: Map<Int, Type>) : ExpressionTypes {
    override fun typeOf(node: ExpressionNode): Type {
        return types[node.nodeId]!!
    }
}
