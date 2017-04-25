package org.shedlang.compiler

import org.shedlang.compiler.ast.ImportPath
import org.shedlang.compiler.ast.ImportPathBase
import org.shedlang.compiler.ast.ModuleNode
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.parser.parse
import org.shedlang.compiler.typechecker.*
import java.io.File
import java.nio.file.Path


class FrontEndResult(
    val module: ModuleNode,
    val references: ResolvedReferences,
    val moduleType: ModuleType
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

fun read(path: Path): FrontEndResult {
    val module = parse(filename = path.toString(), input = path.toFile().readText())

    val resolvedReferences = resolve(module, mapOf(
        "Unit" to unitTypeNodeId,
        "Int" to intTypeNodeId,
        "String" to stringTypeNodeId,

        "!io" to ioEffectNodeId,

        "print" to printNodeId,
        "intToString" to intToStringNodeId
    ))

    val typeCheckResult = typeCheck(
        module,
        nodeTypes = globalNodeTypes,
        resolvedReferences = resolvedReferences,
        getModule = { importPath ->
            val result = read(resolveModule(path, importPath))
            result.moduleType
        }
    )
    checkReturns(module, typeCheckResult.types)
    return FrontEndResult(
        module = module,
        references = resolvedReferences,
        moduleType = typeCheckResult.moduleType
    )
}

fun resolveModule(path: Path, importPath: ImportPath): Path {
    // TODO: test this properly
    val base = when (importPath.base) {
        ImportPathBase.Absolute -> throw UnsupportedOperationException()
        is ImportPathBase.Relative -> path
    }

    return base.resolveSibling(importPath.parts.joinToString(File.separator) + ".shed")
}
