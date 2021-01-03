package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmInstruction
import org.shedlang.compiler.types.ModuleType
import org.shedlang.compiler.types.ShapeType
import org.shedlang.compiler.types.Type
import java.lang.UnsupportedOperationException

internal object WasmObjects {
    internal fun compileObjectStore(
        objectPointer: WasmInstruction.Folded,
        objectType: Type,
        fieldValues: List<Pair<Identifier, WasmInstruction.Folded>>,
        context: WasmFunctionContext,
    ): WasmFunctionContext {
        return fieldValues.fold(context) { currentContext, (fieldName, fieldValue) ->
            currentContext
                .addInstruction(compileFieldStore(
                    objectPointer = objectPointer,
                    objectType = objectType,
                    fieldName = fieldName,
                    fieldValue = fieldValue,
                ))
        }
    }

    private fun compileFieldStore(
        objectPointer: WasmInstruction.Folded,
        objectType: Type,
        fieldName: Identifier,
        fieldValue: WasmInstruction.Folded,
    ): WasmInstruction.Folded {
        val layout = layout(objectType)

        return Wasm.I.i32Store(
            address = objectPointer,
            offset = layout.fieldOffset(objectType, fieldName),
            alignment = WasmData.VALUE_SIZE,
            value = fieldValue,
        )
    }

    internal fun compileFieldLoad(objectType: Type, fieldName: Identifier): WasmInstruction {
        val layout = layout(objectType)

        return Wasm.I.i32Load(
            offset = layout.fieldOffset(
                objectType = objectType,
                fieldName = fieldName,
            ),
            alignment = WasmData.VALUE_SIZE,
        )
    }

    private fun layout(type: Type): Layout {
        val fieldNames = when (type) {
            is ModuleType -> type.fields.keys
            is ShapeType -> type.allFields.keys
            else -> throw UnsupportedOperationException()
        }.sorted()

        return Layout(fieldNames = fieldNames)
    }

    class Layout(private val fieldNames: List<Identifier>) {
        fun fieldOffset(objectType: Type, fieldName: Identifier) =
            fieldIndex(type = objectType, fieldName = fieldName) * WasmData.VALUE_SIZE

        fun fieldIndex(type: Type, fieldName: Identifier): Int {
            val fieldIndex = fieldNames.indexOf(fieldName)

            if (fieldIndex == -1) {
                // TODO: better exception
                throw Exception("field not found: ${fieldName.value}")
            } else {
                return fieldIndex
            }
        }
    }
}
