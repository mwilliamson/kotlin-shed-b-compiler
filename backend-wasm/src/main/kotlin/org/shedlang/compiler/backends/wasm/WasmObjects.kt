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
        val layout = layout(type = objectType)
        return fieldValues.fold(context) { currentContext, (fieldName, fieldValue) ->
            currentContext
                .addInstruction(Wasm.I.i32Store(
                    address = objectPointer,
                    offset = layout.fieldOffset(fieldName = fieldName),
                    alignment = WasmData.VALUE_SIZE,
                    value = fieldValue,
                ))
        }
    }

    internal fun compileFieldLoad(objectType: Type, fieldName: Identifier): WasmInstruction {
        val layout = layout(objectType)

        return Wasm.I.i32Load(
            offset = layout.fieldOffset(
                fieldName = fieldName,
            ),
            alignment = WasmData.VALUE_SIZE,
        )
    }

    fun layout(type: Type): Layout {
        val fieldNames = when (type) {
            is ModuleType -> type.fields.keys
            is ShapeType -> type.allFields.keys
            else -> throw UnsupportedOperationException()
        }.sorted()

        return Layout(fieldNames = fieldNames)
    }

    class Layout(private val fieldNames: List<Identifier>) {
        val size: Int
            get() = fieldNames.size * WasmData.VALUE_SIZE

        val alignment: Int
            get() = WasmData.VALUE_SIZE

        fun fieldOffset(fieldName: Identifier) =
            fieldIndex(fieldName = fieldName) * WasmData.VALUE_SIZE

        fun fieldIndex(fieldName: Identifier): Int {
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
