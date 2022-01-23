package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmInstruction
import org.shedlang.compiler.types.*
import java.lang.UnsupportedOperationException

internal object WasmObjects {
    private const val OBJECT_ALIGNMENT = WasmData.VALUE_SIZE

    internal fun compileObjectStore(
        objectPointer: WasmInstruction.Folded,
        layout: ShapeLayout,
        fieldValues: List<Pair<Identifier, WasmInstruction.Folded>>,
        context: WasmFunctionContext,
    ): WasmFunctionContext {
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

    internal fun compileFieldUpdate(
        objectType: ShapeType,
        fieldName: Identifier,
        context: WasmFunctionContext
    ): WasmFunctionContext {
        val layout = shapeLayout(objectType)

        val (context2, newFieldValue) = context.addLocal("newFieldValue")
        val context3 = context2.addInstruction(newFieldValue.set())

        val (context4, originalObjectPointer) = context3.addLocal("originalObj")
        val context5 = context4.addInstruction(originalObjectPointer.set())

        val (context6, updatedObjectPointer) = malloc("updatedObj", layout, context5)

        val context7 = compileObjectStore(
            objectPointer = updatedObjectPointer.get(),
            layout = layout,
            fieldValues = objectType.fields.values.map { field ->
                val fieldValue = if (field.name == fieldName) {
                    newFieldValue.get()
                } else {
                    Wasm.I.i32Load(
                        address = originalObjectPointer.get(),
                        offset = layout.fieldOffset(fieldName = field.name),
                        alignment = OBJECT_ALIGNMENT,
                    )
                }
                field.name to fieldValue
            },
            context = context6,
        )

        return context7.addInstruction(updatedObjectPointer.get())
    }

    fun layout(type: Type): Layout {
        return when (type) {
            is ModuleType -> moduleLayout(type)
            is ShapeType -> shapeLayout(type)
            is StaticValueType -> shapeTypeLayout(type)
            else -> throw UnsupportedOperationException("layout unknown for type: $type")
        }
    }

    internal interface Layout {
        val alignment: Int
        val size: Int
        fun fieldOffset(fieldName: Identifier): Int
    }

    fun moduleLayout(type: ModuleType): ShapeLayout {
        return ShapeLayout(fieldNames = type.fields.keys, tagValue = null)
    }

    fun shapeLayout(type: ShapeType): ShapeLayout {
        return ShapeLayout(fieldNames = type.fields.keys, tagValue = type.tagValue)
    }

    internal class ShapeLayout(fieldNames: Collection<Identifier>, val tagValue: TagValue?): Layout {
        private val tagValueSize = if (tagValue == null) 0 else WasmData.TAG_VALUE_SIZE
        private val fieldsLayout = FieldsLayout(fieldNames = fieldNames)

        override val size: Int
            get() = tagValueSize + fieldsLayout.size

        override val alignment: Int
            get() = OBJECT_ALIGNMENT

        val tagValueOffset: Int = 0

        override fun fieldOffset(fieldName: Identifier) =
            tagValueSize + fieldsLayout.fieldOffset(fieldName)
    }

    internal fun shapeTypeLayout(type: StaticValueType): ShapeTypeLayout {
        return ShapeTypeLayout(fieldNames = type.fields!!.keys)
    }

    internal class ShapeTypeLayout(fieldNames: Collection<Identifier>): Layout {
        private val fieldsLayout = FieldsLayout(fieldNames = fieldNames)

        override val alignment: Int
            get() = WasmData.closureAlignment

        override val size: Int
            get() = WasmData.FUNCTION_POINTER_SIZE + WasmData.TAG_VALUE_SIZE + fieldsLayout.size

        val closureOffset: Int
            get() = 0

        val tagValueOffset: Int
            get() = WasmData.FUNCTION_POINTER_SIZE

        override fun fieldOffset(fieldName: Identifier): Int {
            return tagValueOffset + WasmData.TAG_VALUE_SIZE + fieldsLayout.fieldOffset(fieldName)
        }
    }

    internal class FieldsLayout(fieldNames: Collection<Identifier>) {
        private val fieldNames = fieldNames.sorted()

        val size: Int
            get() = fieldNames.size * WasmData.VALUE_SIZE

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
