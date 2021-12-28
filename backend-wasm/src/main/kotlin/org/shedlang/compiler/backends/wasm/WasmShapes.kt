package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmConstValue
import org.shedlang.compiler.backends.wasm.wasm.WasmParam
import org.shedlang.compiler.types.Field
import org.shedlang.compiler.types.ShapeType

internal object WasmShapes {
    internal fun compileConstructor(
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
