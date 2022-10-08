package org.shedlang.compiler.types

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.ModuleName

data class QualifiedName(val parts: List<QualifiedNamePart>, val shortName: Identifier) {
    fun addTypeName(name: String): QualifiedName {
        val shortName = Identifier(name)

        return QualifiedName(parts + QualifiedNamePart.Type(shortName), shortName)
    }

    companion object {
        fun type(prefix: List<QualifiedNamePart>, shortName: Identifier): QualifiedName {
            return QualifiedName(prefix + QualifiedNamePart.Type(shortName), shortName)
        }

        fun builtin(name: String): QualifiedName {
            val shortName = Identifier(name)
            return QualifiedName(listOf(QualifiedNamePart.Type(shortName)), shortName)
        }

        fun topLevelType(moduleName: List<String>, typeName: String): QualifiedName {
            return topLevelType(moduleName.map(::Identifier), Identifier(typeName))
        }

        fun topLevelType(moduleName: ModuleName, typeName: Identifier): QualifiedName {
            return QualifiedName(
                listOf(QualifiedNamePart.Module(moduleName), QualifiedNamePart.Type(typeName)),
                typeName
            )
        }
    }
}

sealed interface QualifiedNamePart {
    data class Module(val name: ModuleName): QualifiedNamePart
    data class Type(val name: Identifier): QualifiedNamePart
}
