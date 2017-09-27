package org.shedlang.compiler

import com.moandjiezana.toml.Toml
import org.shedlang.compiler.ast.FunctionDeclarationNode
import org.shedlang.compiler.ast.ImportPath
import org.shedlang.compiler.ast.ImportPathBase
import org.shedlang.compiler.ast.ModuleNode
import org.shedlang.compiler.parser.parse
import org.shedlang.compiler.typechecker.ResolvedReferences
import org.shedlang.compiler.typechecker.resolve
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.ModuleType
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths


interface FrontEndResult {
    val modules: List<Module>
    fun importToModule(modulePath: Path, importPath: ImportPath): Module
}

class Module(
    val sourcePath: Path,
    val destinationPath: List<String>,
    val node: ModuleNode,
    val type: ModuleType,
    val references: ResolvedReferences
) {
    fun hasMain() = node.body.any({ node ->
        node is FunctionDeclarationNode && node.name == "main"
    })
}

fun read(base: Path, path: Path): FrontEndResult {
    return readAll(base, listOf(path))
}

fun readDirectory(base: Path): FrontEndResult {
    if (base.toFile().isFile) {
        return read(base = base.parent, path = base.fileName)
    } else {
        val paths = base.toFile()
            .walk()
            .filter({ file -> file.path.endsWith(".shed") })
            .map({ file -> file.toPath() })

        return readAll(base = base, paths = paths.asIterable())
    }
}

private fun readAll(base: Path, paths: Iterable<Path>): FrontEndResult {
    val modules = Modules(base = base)
    paths.forEach({ path -> modules.pathToModule(path) })
    return modules
}

private class Modules(private val base: Path): FrontEndResult {
    private val pathToModule = HashMap<Path, Module>()
    private val imports = HashMap<Pair<Path, ImportPath>, Path>()

    override val modules: List<Module>
        get() = pathToModule.values.toList()

    fun pathToModule(path: Path): Module {
        return pathToModule.computeIfAbsent(path, { path ->
            readModule(base = base, path = path, importToModule = this::importToModule)
        })
    }

    override fun importToModule(modulePath: Path, importPath: ImportPath): Module {
        return pathToModule(resolveModule(modulePath, importPath))
    }

    fun resolveModule(modulePath: Path, importPath: ImportPath): Path {
        return imports.computeIfAbsent(Pair(modulePath, importPath), { (modulePath, importPath) ->
            // TODO: test this properly
            when (importPath.base) {
                is ImportPathBase.Absolute -> {
                    val packageName = importPath.parts[0]
                    val config = readPackageConfig(path = modulePath)
                    val packagePath = config.resolveDependency(packageName = packageName)
                    packagePath.resolve(importPath.parts.drop(1).joinToString(File.separator) + ".shed")
                }
                is ImportPathBase.Relative -> {
                    modulePath.resolveSibling(importPath.parts.joinToString(File.separator) + ".shed")
                }
            }
        })
    }
}

private fun readModule(base: Path, path: Path, importToModule: (Path, ImportPath) -> Module): Module {
    val moduleNode = parse(filename = path.toString(), input = path.toFile().readText())

    val resolvedReferences = resolve(
        moduleNode,
        builtins.associate({ builtin -> builtin.name to builtin.nodeId})
    )

    val typeCheckResult = typeCheck(
        moduleNode,
        nodeTypes = builtins.associate({ builtin -> builtin.nodeId to builtin.type}),
        resolvedReferences = resolvedReferences,
        getModule = { importPath ->
            val result = importToModule(path, importPath)
            result.type
        }
    )

    return Module(
        sourcePath = path,
        destinationPath = identifyModule(base = base, path = path),
        node = moduleNode,
        type = typeCheckResult.moduleType,
        references = resolvedReferences
    )
}

fun identifyModule(base: Path, path: Path): List<String> {
    val targetPath = if (path.startsWith(base)) {
        base.relativize(path)
    } else {
        val packageConfig = readPackageConfig(path)
        // TODO: throw a better error
        val name = packageConfig.name()
        // TODO: handle packages with the same name
        Paths.get("dependencies").resolve(name).resolve(packageConfig.packagePath.relativize(path))
    }
    val pathParts = targetPath.map(Path::toString)
    return pathParts.take(pathParts.size - 1) + pathParts.last().removeSuffix(".shed")
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
