package org.shedlang.compiler

import org.shedlang.compiler.ast.ImportPath
import org.shedlang.compiler.ast.ImportPathBase
import org.shedlang.compiler.ast.ModuleName


fun resolveImport(importingModuleName: ModuleName, importPath: ImportPath): ModuleName {
    return when (importPath.base) {
        ImportPathBase.Relative -> {
            importingModuleName.dropLast(1) + importPath.parts
        }
        ImportPathBase.Absolute -> {
            importPath.parts
        }
    }
}
