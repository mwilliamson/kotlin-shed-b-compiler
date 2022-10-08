package org.shedlang.compiler.types

import org.shedlang.compiler.ast.Identifier

interface TypeRegistry {
    fun fieldType(type: Type, fieldName: Identifier): Type?

    companion object {
        val Empty : TypeRegistry = TypeRegistryImpl()
    }
}

class TypeRegistryImpl: TypeRegistry {
    override fun fieldType(type: Type, fieldName: Identifier): Type? {
        val fields = type.fields
        return if (fields == null) {
            null
        } else {
            fields[fieldName]?.type
        }
    }
}
