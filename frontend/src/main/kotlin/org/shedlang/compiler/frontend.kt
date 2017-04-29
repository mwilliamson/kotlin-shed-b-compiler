package org.shedlang.compiler

import org.shedlang.compiler.ast.ImportPath
import org.shedlang.compiler.ast.ImportPathBase
import org.shedlang.compiler.ast.ModuleNode
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.parser.parse
import org.shedlang.compiler.typechecker.*
import java.io.File
import java.nio.file.Path
import java.util.*


class FrontEndResult(
    val modules: List<Module>
)

class Module(
    val path: List<String>,
    val node: ModuleNode,
    val type: ModuleType,
    val references: ResolvedReferences
)

private val intTypeNodeId = freshNodeId()
private val unitTypeNodeId = freshNodeId()
private val stringTypeNodeId = freshNodeId()

private val ioEffectNodeId = freshNodeId()

private val printNodeId = freshNodeId()
private val intToStringNodeId = freshNodeId()

private val globalNodeTypes = mapOf(
    unitTypeNodeId to MetaType(UnitType),
    intTypeNodeId to MetaType(IntType),
    stringTypeNodeId to MetaType(StringType),

    ioEffectNodeId to EffectType(IoEffect),

    printNodeId to FunctionType(
        positionalArguments = listOf(StringType),
        namedArguments = mapOf(),
        effects = listOf(IoEffect),
        returns = UnitType
    ),
    intToStringNodeId to positionalFunctionType(listOf(IntType), StringType)
)

fun read(base: Path, path: Path): FrontEndResult {
    val modules = HashMap<Path, Module>()

    fun getModule(path: Path): Module {
        if (!modules.containsKey(path)) {
            modules[path] = readModule(base = base, relativePath = path, getModule = ::getModule)
        }

        return modules[path]!!
    }

    getModule(path)

    return FrontEndResult(modules = modules.values.toList())
}

fun readModule(base: Path, relativePath: Path, getModule: (Path) -> Module): Module {
    val path = base.resolve(relativePath)
    val moduleNode = parse(filename = path.toString(), input = path.toFile().readText())

    val resolvedReferences = resolve(moduleNode, mapOf(
        "Unit" to unitTypeNodeId,
        "Int" to intTypeNodeId,
        "String" to stringTypeNodeId,

        "!io" to ioEffectNodeId,

        "print" to printNodeId,
        "intToString" to intToStringNodeId
    ))

    val typeCheckResult = typeCheck(
        moduleNode,
        nodeTypes = globalNodeTypes,
        resolvedReferences = resolvedReferences,
        getModule = { importPath ->
            val result = getModule(resolveModule(relativePath, importPath))
            result.type
        }
    )
    checkReturns(moduleNode, typeCheckResult.types)

    return Module(
        path = identifyModule(relativePath),
        node = moduleNode,
        type = typeCheckResult.moduleType,
        references = resolvedReferences
    )
}

fun identifyModule(path: Path): List<String> {
    val pathParts = path.map(Path::toString)
    return pathParts.take(pathParts.size - 1) + pathParts.last().removeSuffix(".shed")
}

fun resolveModule(path: Path, importPath: ImportPath): Path {
    // TODO: test this properly
    val base = when (importPath.base) {
        ImportPathBase.Absolute -> throw UnsupportedOperationException()
        is ImportPathBase.Relative -> path
    }

    return base.resolveSibling(importPath.parts.joinToString(File.separator) + ".shed")
}
