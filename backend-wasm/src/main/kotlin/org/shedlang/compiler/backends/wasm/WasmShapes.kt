package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmConstValue
import org.shedlang.compiler.backends.wasm.wasm.WasmDataSegmentKey
import org.shedlang.compiler.backends.wasm.wasm.WasmParam
import org.shedlang.compiler.types.Field
import org.shedlang.compiler.types.ShapeType
import org.shedlang.compiler.types.StaticValueType
import org.shedlang.compiler.types.TagValue

internal object WasmShapes {
    internal fun compileDefineShape(
        shapeType: ShapeType,
        metaType: StaticValueType,
        context: WasmFunctionContext
    ): WasmFunctionContext {
        val metaTypeLayout = WasmObjects.shapeTypeLayout(metaType)
        val (context2, shape) = malloc("shape", metaTypeLayout, context)
        return WasmShapeCompiler(shapeType = shapeType, metaTypeLayout = metaTypeLayout, metaTypePointer = shape)
            .compileDefineShape(context2)
    }
}

private class WasmShapeCompiler(
    private val shapeType: ShapeType,
    private val metaTypeLayout: WasmObjects.ShapeTypeLayout,
    private val metaTypePointer: String,
) {
    fun compileDefineShape(context: WasmFunctionContext): WasmFunctionContext {
        val (context2, constructorTableIndex) = compileConstructor(context)
        val context3 = compileStoreConstructor(constructorTableIndex, context2)

        val context4 = compileStoreTagValue(context3)

        val (context5, nameMemoryIndex) = context4.addSizedStaticUtf8String(shapeType.name.value)
        val context6 = compileStoreName(context5, nameMemoryIndex)

        return context6.addInstruction(Wasm.I.localGet(metaTypePointer))
    }

    private fun compileConstructor(
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

    private fun compileStoreConstructor(
        constructorTableIndex: WasmConstValue.TableEntryIndex,
        context: WasmFunctionContext
    ): WasmFunctionContext {
        return context.addInstruction(
            Wasm.I.i32Store(
                address = Wasm.I.localGet(metaTypePointer),
                offset = metaTypeLayout.closureOffset,
                value = Wasm.I.i32Const(constructorTableIndex),
            )
        )
    }

    private fun compileStoreTagValue(
        context: WasmFunctionContext
    ): WasmFunctionContext {
        val tagValue = shapeType.tagValue
        return if (tagValue == null) {
            context
        } else {
            context.addInstruction(
                Wasm.I.i32Store(
                    address = Wasm.I.localGet(metaTypePointer),
                    offset = metaTypeLayout.tagValueOffset,
                    value = Wasm.I.i32Const(WasmConstValue.TagValue(tagValue)),
                )
            )
        }
    }

    private fun compileStoreName(
        context7: WasmFunctionContext,
        nameMemoryIndex: WasmDataSegmentKey
    ) = context7.addInstruction(
        Wasm.I.i32Store(
            address = Wasm.I.localGet(metaTypePointer),
            offset = metaTypeLayout.fieldOffset(Identifier("name")),
            value = Wasm.I.i32Const(nameMemoryIndex),
        )
    )
}
