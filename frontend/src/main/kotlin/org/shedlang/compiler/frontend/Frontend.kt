package org.shedlang.compiler.frontend

import com.moandjiezana.toml.Toml
import org.shedlang.compiler.*
import org.shedlang.compiler.ast.*
import org.shedlang.compiler.parser.parse
import org.shedlang.compiler.parser.parseTypesModule
import org.shedlang.compiler.typechecker.PackageConfigError
import org.shedlang.compiler.typechecker.resolveReferences
import org.shedlang.compiler.typechecker.typeCheck
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern


fun readStandaloneModule(path: Path): ModuleSet {
    val absolutePath = path.toAbsolutePath()
    val moduleName = standaloneModulePathToName(path)
    val reader = ModuleReader(sourceDirectories = listOf(absolutePath.parent), implicitStdlib = true)
    reader.load(moduleName)
    return ModuleSet(reader.modules)
}

fun standaloneModulePathToName(path: Path): ModuleName {
    return listOf(Identifier(path.fileName.toString().removeSuffix(".shed")))
}

private const val sourceDirectoryName = "src"

fun readPackageModule(base: Path, name: ModuleName): ModuleSet {
    val reader = createModuleReader(base)
    reader.load(name)
    return ModuleSet(reader.modules)
}

fun readPackage(base: Path): ModuleSet {
    val reader = createModuleReader(base)

    val namePattern = Pattern.compile("^([^.]+)(\\.types)?\\.shed$")

    val sourceDirectory = base.resolve(sourceDirectoryName)

    for (file in sourceDirectory.toFile().walk()) {
        val match = namePattern.matcher(file.name)
        if (match.find()) {
            val directoryNames = sourceDirectory.relativize(file.toPath().parent).map { part -> part.toString() }
            val moduleName = (directoryNames + listOf(match.group(1))).map(::Identifier)
            reader.load(moduleName)
        }
    }

    return ModuleSet(reader.modules)
}

private fun createModuleReader(base: Path): ModuleReader {
    val dependencyDirectories = base.resolve(dependenciesDirectoryName).toFile().listFiles()
        ?: arrayOf<File>()
    val dependencies = dependencyDirectories
        .map { file -> file.resolve(sourceDirectoryName) }
        .filter { file -> file.exists() && file.isDirectory }
        .map { file -> file.toPath() }
    val sourceDirectories = listOf(base.resolve(sourceDirectoryName)) + dependencies

    return ModuleReader(sourceDirectories = sourceDirectories, implicitStdlib = false)
}

private fun readModule(
    path: Path,
    name: ModuleName,
    getModule: (ModuleName) -> ModuleResult
): Module {
    val moduleText = path.toFile().readText()

    val nodeTypes = builtins.associate { builtin -> builtin.nodeId to builtin.type }
    val importPathToModule: (ImportPath) -> ModuleResult = { importPath ->
        val fullPath = resolveImport(name, importPath)
        getModule(fullPath)
    }

    if (path.toString().endsWith(".types.shed")) {
        val parsedModuleNode = parseTypesModule(filename = path.toString(), input = moduleText)
        val moduleNode = parsedModuleNode.copy(
            imports = addCoreModuleImports(name = name, imports = parsedModuleNode.imports)
        )
        val resolvedReferences = resolveModuleReferences(moduleNode)

        val typeCheckResult = typeCheck(
            name,
            moduleNode,
            nodeTypes = nodeTypes,
            resolvedReferences = resolvedReferences,
            getModule = importPathToModule
        )

        return Module.Native(
            name = name,
            type = typeCheckResult.moduleType
        )
    } else {
        val parsedModuleNode = parse(filename = path.toString(), input = moduleText)
        val moduleNode = parsedModuleNode.copy(
            imports = addCoreModuleImports(name = name, imports = parsedModuleNode.imports)
        )
        val resolvedReferences = resolveModuleReferences(moduleNode)

        checkTailCalls(moduleNode, references = resolvedReferences)
        val typeCheckResult = typeCheck(
            name,
            moduleNode,
            nodeTypes = nodeTypes,
            resolvedReferences = resolvedReferences,
            getModule = importPathToModule
        )

        return Module.Shed(
            name = name,
            node = moduleNode,
            type = typeCheckResult.moduleType,
            types = typeCheckResult.types,
            references = resolvedReferences
        )
    }
}

private fun addCoreModuleImports(name: ModuleName, imports: List<ImportNode>): List<ImportNode> {
    return if (isCoreModule(name)) {
        imports
    } else {
        coreImports + imports
    }
}

private fun isCoreModule(moduleName: ModuleName): Boolean {
    return moduleName.isNotEmpty() && moduleName[0] == Identifier("Core")
}

private val castImport = createCoreImport(
    "Cast",
    listOf("cast")
)

private val intToStringImport = createCoreImport(
    "IntToString",
    listOf("intToString")
)

private val ioImport = createCoreImport(
    "Io",
    listOf("print")
)

private val optionsImport = createCoreImport(
    "Options",
    listOf("Option", "Some", "None", "some", "none")
)

private fun createCoreImport(module: String, names: List<String>): ImportNode {
    return ImportNode(
        target = TargetNode.Fields(
            names.map { importName ->
                FieldNameNode(
                    Identifier(importName),
                    source = BuiltinSource
                ) to TargetNode.Variable(
                    Identifier(importName),
                    source = BuiltinSource
                )
            },
            source = BuiltinSource
        ),
        path = ImportPath.absolute(listOf("Core", module)),
        source = BuiltinSource
    )
}

private val coreImports = listOf(castImport, intToStringImport, ioImport, optionsImport)

private fun resolveModuleReferences(moduleNode: Node): ResolvedReferences {
    return resolveReferences(
        moduleNode,
        builtins.associate { builtin -> builtin.name to builtin }
    )
}

internal sealed class ModuleResult(val name: ModuleName) {
    class NotFound(name: ModuleName): ModuleResult(name)
    class Found(val module: Module): ModuleResult(module.name)
    class FoundMany(name: ModuleName): ModuleResult(name)
}

private class ModuleReader(
    private val sourceDirectories: List<Path>,
    private val implicitStdlib: Boolean
) {
    private val modulesByName = LazyMap<ModuleName, ModuleResult> { name ->
        readModuleInPackage(name = name)
    }

    internal fun load(name: ModuleName) {
        val module = modulesByName.get(name)
        when (module) {
            is ModuleResult.Found -> {}

            is ModuleResult.NotFound ->
                throw Exception("module not found: " + formatModuleName(name))

            is ModuleResult.FoundMany ->
                throw Exception("module is ambiguous: " + formatModuleName(name))
        }
    }

    internal val modules: Collection<Module>
        get() = modulesByName.values.flatMap { result ->
            if (result is ModuleResult.Found) {
                listOf(result.module)
            } else {
                listOf()
            }
        }

    private fun readModuleInPackage(name: ModuleName): ModuleResult {
        val bases = listOf(findRoot().resolve("corelib").resolve(sourceDirectoryName)) +
            (if (implicitStdlib) listOf(findRoot().resolve("stdlib").resolve(sourceDirectoryName)) else listOf()) +
            sourceDirectories

        val possiblePaths = bases.flatMap { base ->
            listOf(".shed", ".types.shed").map { extension ->
                pathAppend(name.fold(base, { path, part -> path.resolve(part.value) }), extension)
            }
        }
        val matchingPaths = possiblePaths.filter { path -> path.toFile().exists() }
        if (matchingPaths.size == 0) {
            return ModuleResult.NotFound(name)
        } else if (matchingPaths.size > 1) {
            return ModuleResult.FoundMany(name)
        } else {
            val module = readModule(
                path = matchingPaths.single(),
                name = name,
                getModule = { name ->
                    modulesByName.get(name)
                }
            )
            return ModuleResult.Found(module)
        }
    }
}

private fun pathAppend(path: Path, suffix: String): Path {
    return path.resolveSibling(path.fileName.toString() + suffix)
}

fun installDependencies(path: Path) {
    val packageConfig = readPackageConfig(path)
    packageConfig.dependencies().forEach { dependency ->
        installDependency(path, dependency)
    }
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

private const val dependenciesDirectoryName = "shedDependencies"

private fun readPackageConfig(path: Path): PackageConfig {
    val configPath = path.resolve("shed.toml")
    if (configPath.toFile().exists()) {
        return TomlPackageConfig(
            path = configPath,
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

private object EmptyPackageConfig : PackageConfig {
    override fun name(): String? {
        return null
    }

    override fun dependencies(): List<Dependency> {
        return listOf()
    }
}

private class TomlPackageConfig(private val path: Path, private val config: Toml) : PackageConfig {
    override fun name(): String {
        return config.getString("package.name")
            .orElseThrow(PackageConfigError("Package is missing name", source = FileSource(filename = path.toString())))
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
