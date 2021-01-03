package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmInstruction
import org.shedlang.compiler.types.ModuleType
import org.shedlang.compiler.types.ShapeType
import org.shedlang.compiler.types.Type
import java.lang.UnsupportedOperationException

internal object WasmObjects {
    internal fun compileFieldStore(
        objectPointer: WasmInstruction.Folded,
        objectType: Type,
        fieldName: Identifier,
        fieldValue: WasmInstruction.Folded,
    ): WasmInstruction.Folded {
        return Wasm.I.i32Store(
            address = objectPointer,
            offset = fieldOffset(objectType, fieldName),
            alignment = WasmData.VALUE_SIZE,
            value = fieldValue,
        )
    }

    internal fun compileFieldLoad(objectType: Type, fieldName: Identifier): WasmInstruction {
        return Wasm.I.i32Load(
            offset = fieldOffset(
                objectType = objectType,
                fieldName = fieldName,
            ),
            alignment = WasmData.VALUE_SIZE,
        )
    }

    private fun fieldOffset(objectType: Type, fieldName: Identifier) =
        fieldIndex(type = objectType, fieldName = fieldName) * WasmData.VALUE_SIZE

    private fun fieldIndex(type: Type, fieldName: Identifier): Int {
        val fieldNames = when (type) {
            is ModuleType -> type.fields.keys
            is ShapeType -> type.allFields.keys
            else -> throw UnsupportedOperationException()
        }
        val sortedFieldNames = fieldNames.sorted()
        val fieldIndex = sortedFieldNames.indexOf(fieldName)

        if (fieldIndex == -1) {
            // TODO: better exception
            throw Exception("field not found: ${fieldName.value}")
        } else {
            return fieldIndex
        }
    }
}
