package org.shedlang.compiler

import com.moandjiezana.toml.Toml
import org.shedlang.compiler.ast.ImportPathBase
import org.shedlang.compiler.parser.parse
import org.shedlang.compiler.typechecker.resolve
import org.shedlang.compiler.typechecker.typeCheck
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

    return ModuleSet(listOf(module))
}

fun readPackage(base: Path, name: List<String>): ModuleSet {
    val reader = ModuleReader(root = base)
    reader.load(name)
    return ModuleSet(reader.modules)
}

private fun readModule(path: Path, name: List<String>, getModule: (List<String>) -> Module): Module {
    val moduleNode = parse(filename = path.toString(), input = path.toFile().readText())

    val resolvedReferences = resolve(
        moduleNode,
        builtins.associate({ builtin -> builtin.name to builtin })
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

    return Module.Shed(
        name = name,
        node = moduleNode,
        type = typeCheckResult.moduleType,
        expressionTypes = typeCheckResult.expressionTypes,
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
        val dependencyDirectories = root.resolve(dependenciesDirectoryName).toFile().listFiles() ?: arrayOf<File>()
        val sourceDirectoryName = "src"
        val dependencies = dependencyDirectories
            .map({ file -> file.resolve(sourceDirectoryName) })
            .filter({ file -> file.exists() && file.isDirectory })
            .map({ file -> file.toPath() })
        val bases = listOf(root.resolve(sourceDirectoryName)) + dependencies
        val possiblePaths = bases.map({ base ->
            pathAppend(name.fold(base, { path, part -> path.resolve(part) }), ".shed")
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
