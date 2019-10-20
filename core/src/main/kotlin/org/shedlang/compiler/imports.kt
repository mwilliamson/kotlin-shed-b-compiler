package org.shedlang.compiler

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ImportPath
import org.shedlang.compiler.ast.ImportPathBase


fun resolveImport(importingModuleName: List<Identifier>, importPath: ImportPath): List<Identifier> {
    return when (importPath.base) {
        ImportPathBase.Relative -> {
            importingModuleName.dropLast(1) + importPath.parts
        }
        ImportPathBase.Absolute -> {
            importPath.parts
        }
    }
}
