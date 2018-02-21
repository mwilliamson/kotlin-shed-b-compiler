package org.shedlang.compiler

import com.moandjiezana.toml.Toml
import org.shedlang.compiler.ast.ImportPath
import org.shedlang.compiler.ast.ImportPathBase
import org.shedlang.compiler.ast.Node
import org.shedlang.compiler.parser.parse
import org.shedlang.compiler.parser.parseTypesModule
import org.shedlang.compiler.typechecker.resolve
import org.shedlang.compiler.typechecker.typeCheck
import org.shedlang.compiler.types.ModuleType
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


fun readStandalone(path: Path): ModuleSet {
    val module = readModule(
        path = path,
        name = listOf("main"),
        getModule = { throw UnsupportedOperationException("Standalone programs cannot import") }
    )

    if (module == null) {
        // TODO: throw better exception
        throw Exception("Could not find module")
    } else {
        return ModuleSet(listOf(module))
    }

}

fun readPackage(base: Path, name: List<String>): ModuleSet {
    val reader = ModuleReader(root = base)
    reader.load(name)
    return ModuleSet(reader.modules)
}

private fun readModule(path: Path, name: List<String>, getModule: (List<String>) -> Module?): Module? {
    val moduleText = path.toFile().readText()

    val nodeTypes = builtins.associate({ builtin -> builtin.nodeId to builtin.type })
    val importPathToModule: (ImportPath) -> ModuleType? = { importPath ->
        when (importPath.base) {
            ImportPathBase.Relative -> {
                val importedModuleName = resolveName(name, importPath.parts)
                getModule(importedModuleName)?.type
            }
            ImportPathBase.Absolute -> {
                getModule(importPath.parts)?.type
            }
        }
    }

    if (path.toString().endsWith(".types.shed")) {
        val moduleNode = parseTypesModule(filename = path.toString(), input = moduleText)
        val resolvedReferences = resolveModuleReferences(moduleNode)

        val typeCheckResult = typeCheck(
            moduleNode,
            nodeTypes = nodeTypes,
            resolvedReferences = resolvedReferences,
            getModule = importPathToModule
        )

        return Module.Native(
            name = name,
            type = typeCheckResult.moduleType,
            filePath = path
        )
    } else {
        val moduleNode = parse(filename = path.toString(), input = moduleText)
        val resolvedReferences = resolveModuleReferences(moduleNode)

        val typeCheckResult = typeCheck(
            moduleNode,
            nodeTypes = nodeTypes,
            resolvedReferences = resolvedReferences,
            getModule = importPathToModule
        )

        return Module.Shed(
            name = name,
            node = moduleNode,
            type = typeCheckResult.moduleType,
            expressionTypes = typeCheckResult.expressionTypes,
            references = resolvedReferences
        )
    }
}

private fun resolveModuleReferences(moduleNode: Node): ResolvedReferences {
    return resolve(
        moduleNode,
        builtins.associate({ builtin -> builtin.name to builtin })
    )
}

private class ModuleReader(private val root: Path) {
    private val modulesByName = LazyMap<List<String>, Module?>({ name ->
        readModuleInPackage(name = name)
    })

    internal fun load(name: List<String>) {
        modulesByName.get(name)
    }

    internal val modules: Collection<Module>
        get() = modulesByName.values.filterNotNull()

    private fun readModuleInPackage(name: List<String>): Module? {
        val dependencyDirectories = root.resolve(dependenciesDirectoryName).toFile().listFiles() ?: arrayOf<File>()
        val sourceDirectoryName = "src"
        val dependencies = dependencyDirectories
            .map({ file -> file.resolve(sourceDirectoryName) })
            .filter({ file -> file.exists() && file.isDirectory })
            .map({ file -> file.toPath() })
        val bases = listOf(root.resolve(sourceDirectoryName)) + dependencies
        val possiblePaths = bases.flatMap({ base ->
            listOf(".shed", ".types.shed").map { extension ->
                pathAppend(name.fold(base, { path, part -> path.resolve(part) }), extension)
            }
        })
        val matchingPaths = possiblePaths.filter({ path -> path.toFile().exists() })
        if (matchingPaths.size == 0) {
            throw Exception("Could not find module: " + name.joinToString("."))
        } else if (matchingPaths.size > 1) {
            throw Exception("Multiple matches for module: " + name.joinToString("."))
        } else {
            return readModule(
                path = matchingPaths.single(),
                name = name,
                getModule = { name ->
                    modulesByName.get(name)
                }
            )
        }
    }
}

private fun pathAppend(path: Path, suffix: String): Path {
    return path.resolveSibling(path.fileName.toString() + suffix)
}

private fun resolveName(base: List<String>, name: List<String>): List<String> {
    return base.dropLast(1) + name
}

fun installDependencies(path: Path) {
    val packageConfig = readPackageConfig(path)
    packageConfig.dependencies().forEach({ dependency ->
        installDependency(path, dependency)
    })
}

private fun installDependency(root: Path, dependency: Dependency) {
    val dependencySourcePath = root.resolve(dependency.source)
    val sourceName = readPackageConfig(dependencySourcePath).name()
    if (sourceName != dependency.name) {
        throw Exception("Dependency name mismatch: expected " + dependency.name + " but was " + sourceName)
    }

    val dependenciesDirectory = root.resolve(dependenciesDirectoryName)
    dependenciesDirectory.toFile().mkdirs()
    val dependencyDestinationPath = dependenciesDirectory.resolve(dependency.name)
    if (dependencyDestinationPath.toFile().exists()) {
        dependencyDestinationPath.toFile().delete()
    }
    Files.createSymbolicLink(dependencyDestinationPath, Paths.get("../").resolve(dependency.source))
}

private val dependenciesDirectoryName = "shedDependencies"

private fun readPackageConfig(path: Path): PackageConfig {
    val configPath = path.resolve("shed.toml")
    if (configPath.toFile().exists()) {
        return TomlPackageConfig(
            config = Toml().read(configPath.toFile())
        )
    } else {
        return EmptyPackageConfig
    }
}

private interface PackageConfig {
    fun name(): String?
    fun dependencies(): List<Dependency>
}

private object EmptyPackageConfig : PackageConfig{
    override fun name(): String? {
        return null
    }

    override fun dependencies(): List<Dependency> {
        return listOf()
    }
}

private class TomlPackageConfig(private val config: Toml) : PackageConfig {
    override fun name(): String {
        // TODO: throw a better error
        return config.getString("package.name").orElseThrow(Exception("Package is missing name"))
    }

    override fun dependencies(): List<Dependency> {
        val dependenciesConfig = config.getTable("dependencies")

        return if (dependenciesConfig == null) {
            listOf()
        } else {
            dependenciesConfig.entrySet().map({ (name, source) ->
                Dependency(name, source as String)
            })
        }
    }
}

private class Dependency(val name: String, val source: String)
