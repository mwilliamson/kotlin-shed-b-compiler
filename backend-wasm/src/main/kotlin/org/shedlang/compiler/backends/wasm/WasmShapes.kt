package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmConstValue
import org.shedlang.compiler.backends.wasm.wasm.WasmParam
import org.shedlang.compiler.types.Field
import org.shedlang.compiler.types.ShapeType
import org.shedlang.compiler.types.StaticValueType

internal object WasmShapes {
    internal fun compileDefineShape(
        shapeType: ShapeType,
        metaType: StaticValueType,
        context: WasmFunctionContext
    ): WasmFunctionContext {
        val tagValue = shapeType.tagValue
        val layout = WasmObjects.shapeTypeLayout(metaType)

        val (context2, shape) = malloc("shape", layout, context)

        val (context4, constructorTableIndex) = compileConstructor(shapeType, context2)
        val context5 = context4.addInstruction(
            Wasm.I.i32Store(
                address = Wasm.I.localGet(shape),
                offset = layout.closureOffset,
                value = Wasm.I.i32Const(constructorTableIndex),
            )
        )
        val context6 = if (tagValue == null) {
            context5
        } else {
            context5.addInstruction(
                Wasm.I.i32Store(
                    address = Wasm.I.localGet(shape),
                    offset = layout.tagValueOffset,
                    value = Wasm.I.i32Const(WasmConstValue.TagValue(tagValue)),
                )
            )
        }

        val (context7, nameMemoryIndex) = context6.addSizedStaticUtf8String(shapeType.name.value)

        val context8 = context7.addInstruction(Wasm.I.i32Store(
            address = Wasm.I.localGet(shape),
            offset = layout.fieldOffset(Identifier("name")),
            value = Wasm.I.i32Const(nameMemoryIndex),
        ))

        return context8.addInstruction(Wasm.I.localGet(shape))
    }

    private fun compileConstructor(
        shapeType: ShapeType,
        context: WasmFunctionContext,
    ): Pair<WasmFunctionContext, WasmConstValue.TableEntryIndex> {
        fun fieldParamIdentifier(field: Field) = "param_${field.name.value}"

        val fields = shapeType.fields.values
        val constructorName = shapeType.name.value
        val layout = WasmObjects.shapeLayout(shapeType)

        return WasmClosures.compileFunction(
            functionName = constructorName,
            freeVariables = listOf(),
            positionalParams = listOf(),
            namedParams = fields.map { field ->
                field.name to WasmParam(
                    fieldParamIdentifier(field),
                    type = WasmData.genericValueType
                )
            },
            compileBody = { constructorContext ->
                val (constructorContext2, obj) = malloc("obj", layout, constructorContext)

                val constructorContext3 = WasmObjects.compileObjectStore(
                    objectPointer = Wasm.I.localGet(obj),
                    layout = layout,
                    fieldValues = fields.map { field ->
                        field.name to Wasm.I.localGet(
                            fieldParamIdentifier(field)
                        )
                    },
                    context = constructorContext2,
                )
                constructorContext3.addInstruction(Wasm.I.localGet(obj))
            },
            context = context,
        )
    }
}
