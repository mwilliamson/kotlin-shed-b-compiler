package org.shedlang.compiler

import com.moandjiezana.toml.Toml
import org.shedlang.compiler.ast.FunctionDeclarationNode
import org.shedlang.compiler.ast.ImportPathBase
import org.shedlang.compiler.ast.ModuleNode
import org.shedlang.compiler.parser.parse
import org.shedlang.compiler.typechecker.ResolvedReferences
import org.shedlang.compiler.typechecker.resolve
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.ModuleType
import java.io.File
import java.nio.file.Path


class FrontEndResult(val modules: Collection<Module>)

class Module(
    val name: List<String>,
    val sourcePath: Path,
    val node: ModuleNode,
    val type: ModuleType,
    val references: ResolvedReferences
) {
    fun hasMain() = node.body.any({ node ->
        node is FunctionDeclarationNode && node.name == "main"
    })
}

fun readStandalone(path: Path): FrontEndResult {
    val module = readModule(
        path = path,
        name = listOf("main"),
        getModule = { throw UnsupportedOperationException("Standalone programs cannot import") }
    )

    return FrontEndResult(listOf(module))
}

fun readPackage(base: Path, name: List<String>): FrontEndResult {
    val reader = ModuleReader(root = base)
    reader.load(name)
    return FrontEndResult(reader.modules)
}

private fun readModule(path: Path, name: List<String>, getModule: (List<String>) -> Module): Module {
    val moduleNode = parse(filename = path.toString(), input = path.toFile().readText())

    val resolvedReferences = resolve(
        moduleNode,
        builtins.associate({ builtin -> builtin.name to builtin.nodeId })
    )

    val typeCheckResult = typeCheck(
        moduleNode,
        nodeTypes = builtins.associate({ builtin -> builtin.nodeId to builtin.type }),
        resolvedReferences = resolvedReferences,
        getModule = { importPath ->
            when (importPath.base) {
                ImportPathBase.Relative -> {
                    val name = resolveName(name, importPath.parts)
                    getModule(name).type
                }
                ImportPathBase.Absolute ->  {
                    getModule(importPath.parts).type
                }
            }
        }
    )

    return Module(
        name = name,
        sourcePath = path,
        node = moduleNode,
        type = typeCheckResult.moduleType,
        references = resolvedReferences
    )
}

private class ModuleReader(private val root: Path) {
    private val modulesByName = LazyMap<List<String>, Module>({ name ->
        readModuleInPackage(name = name)
    })

    internal fun load(name: List<String>) {
        modulesByName.get(name)
    }

    internal val modules: Collection<Module>
        get() = modulesByName.values

    private fun readModuleInPackage(name: List<String>): Module {
        val dependencyDirectories = root.resolve("shedDependencies").toFile().listFiles() ?: arrayOf<File>()
        val sourceDirectoryName = "src"
        val dependencies = dependencyDirectories
            .map({ file -> file.resolve(sourceDirectoryName) })
            .filter({ file -> file.exists() && file.isDirectory })
            .map({ file -> file.toPath() })
        val bases = listOf(root.resolve(sourceDirectoryName)) + dependencies
        val possiblePaths = bases.map({ base ->
            pathAppend(name.fold(base, { path, part -> path.resolve(part) }), ".shed")
        })
        val path = possiblePaths.filter({ path -> path.toFile().exists() }).single()
        return readModule(
            path = path,
            name = name,
            getModule = { name ->
                modulesByName.get(name)
            }
        )
    }
}

private fun pathAppend(path: Path, suffix: String): Path {
    return path.resolveSibling(path.fileName.toString() + suffix)
}

private fun resolveName(base: List<String>, name: List<String>): List<String> {
    return base.dropLast(1) + name
}

private fun readPackageConfig(path: Path): PackageConfig {
    for (parent in path.parents()) {
        val configPath = parent.resolve("shed.toml")
        if (configPath.toFile().exists()) {
            return PackageConfig(
                packagePath = parent,
                config = Toml().read(configPath.toFile())
            )
        }
    }
    // TODO: throw a better error
    throw Exception("Could not read config for " + path)
}

private class PackageConfig(val packagePath: Path, private val config: Toml) {
    fun name(): String {
        // TODO: throw a better error
        return config.getString("package.name").orElseThrow(Exception("Package is missing name"))
    }

    fun resolveDependency(packageName: String): Path {
        val dependencyPath = config.getString("dependencies." + packageName)
        if (dependencyPath == null) {
            // TODO: throw a better error
            throw Exception("Could not find package: " + packageName)
        } else {
            return packagePath.resolve(dependencyPath).toAbsolutePath().normalize()
        }
    }
}
