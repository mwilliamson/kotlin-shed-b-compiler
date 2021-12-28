package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmInstruction
import org.shedlang.compiler.types.ModuleType
import org.shedlang.compiler.types.ShapeType
import org.shedlang.compiler.types.TagValue
import org.shedlang.compiler.types.Type
import java.lang.UnsupportedOperationException

internal object WasmObjects {
    private const val OBJECT_ALIGNMENT = WasmData.VALUE_SIZE

    internal fun compileObjectStore(
        objectPointer: WasmInstruction.Folded,
        objectType: Type,
        fieldValues: List<Pair<Identifier, WasmInstruction.Folded>>,
        context: WasmFunctionContext,
    ): WasmFunctionContext {
        val layout = layout(type = objectType)
        val context2 = if (layout.tagValue == null) {
            context
        } else {
            val newContext = context.compileTagValue(layout.tagValue)
            newContext.addInstruction(Wasm.I.i32Store(
                address = objectPointer,
                offset = layout.tagValueOffset,
                alignment = OBJECT_ALIGNMENT,
                value = Wasm.I.i32Const(layout.tagValue),
            ))
        }
        return fieldValues.fold(context2) { currentContext, (fieldName, fieldValue) ->
            currentContext
                .addInstruction(Wasm.I.i32Store(
                    address = objectPointer,
                    offset = layout.fieldOffset(fieldName = fieldName),
                    alignment = OBJECT_ALIGNMENT,
                    value = fieldValue,
                ))
        }
    }

    internal fun compileTagValueAccess(context: WasmFunctionContext): WasmFunctionContext {
        return context.addInstruction(Wasm.I.i32Load(offset = 0, alignment = OBJECT_ALIGNMENT))
    }

    internal fun compileFieldLoad(objectType: Type, fieldName: Identifier): WasmInstruction {
        val layout = layout(objectType)

        return Wasm.I.i32Load(
            offset = layout.fieldOffset(
                fieldName = fieldName,
            ),
            alignment = OBJECT_ALIGNMENT,
        )
    }

    fun layout(type: Type): Layout {
        val (fieldNames, tagValue) = when (type) {
            is ModuleType -> Pair(type.fields.keys, null)
            is ShapeType -> Pair(type.fields.keys, type.tagValue)
            else -> throw UnsupportedOperationException("layout unknown for type: $type")
        }

        return Layout(fieldNames = fieldNames.sorted(), tagValue = tagValue)
    }

    class Layout(private val fieldNames: Collection<Identifier>, val tagValue: TagValue?) {
        private val tagValueSize = if (tagValue == null) 0 else WasmData.VALUE_SIZE
        private val fieldsLayout = FieldsLayout(fieldNames = fieldNames)

        val size: Int
            get() = tagValueSize + fieldNames.size * WasmData.VALUE_SIZE

        val alignment: Int
            get() = OBJECT_ALIGNMENT

        val tagValueOffset: Int = 0

        fun fieldOffset(fieldName: Identifier) =
            tagValueSize + fieldsLayout.fieldOffset(fieldName)
    }

    internal class ShapeTypeLayout(tagValue: TagValue?) {
        private val tagValueSize = if (tagValue == null) 0 else WasmData.VALUE_SIZE

        val alignment: Int
            get() = WasmData.closureAlignment

        val size: Int
            get() = WasmData.FUNCTION_POINTER_SIZE + tagValueSize

        val closureOffset: Int
            get() = 0

        val tagValueOffset: Int
            get() = WasmData.FUNCTION_POINTER_SIZE
    }

    internal class FieldsLayout(fieldNames: Collection<Identifier>) {
        private val fieldNames = fieldNames.sorted()

        fun fieldOffset(fieldName: Identifier) =
            fieldIndex(fieldName = fieldName) * WasmData.VALUE_SIZE

        private fun fieldIndex(fieldName: Identifier): Int {
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
