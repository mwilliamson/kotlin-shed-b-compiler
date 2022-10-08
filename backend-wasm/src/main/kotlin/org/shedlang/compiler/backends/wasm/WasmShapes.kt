package org.shedlang.compiler.backends.wasm

import org.shedlang.compiler.ModuleSet
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.ast.freshNodeId
import org.shedlang.compiler.backends.wasm.wasm.Wasm
import org.shedlang.compiler.backends.wasm.wasm.WasmConstValue
import org.shedlang.compiler.backends.wasm.wasm.WasmDataSegmentKey
import org.shedlang.compiler.backends.wasm.wasm.WasmParam
import org.shedlang.compiler.stackir.Image
import org.shedlang.compiler.stackir.defineShapeFieldGet
import org.shedlang.compiler.types.*

internal object WasmShapes {
    internal fun compileDefineShape(
        shapeType: ShapeType,
        metaType: TypeLevelValueType,
        context: WasmFunctionContext
    ): WasmFunctionContext {
        val metaTypeLayout = WasmObjects.shapeTypeLayout(metaType)
        val (context2, shape) = malloc("shape", metaTypeLayout, context)
        return WasmShapeCompiler(
            shapeType = shapeType,
            metaType = metaType,
            metaTypeLayout = metaTypeLayout,
            metaTypePointer = shape
        ).compileDefineShape(context2)
    }
}

private class WasmShapeCompiler(
    private val shapeType: ShapeType,
    private val metaType: TypeLevelValueType,
    private val metaTypeLayout: WasmObjects.ShapeTypeLayout,
    private val metaTypePointer: WasmLocalRef,
) {
    fun compileDefineShape(context: WasmFunctionContext): WasmFunctionContext {
        val (context2, constructorTableIndex) = compileConstructor(context)
        val context3 = compileStoreConstructor(constructorTableIndex, context2)

        val context4 = compileStoreTagValue(context3)

        val (context5, fieldsObjectPointer) = compileCreateFieldsObject(context4)
        val context6 = compileStoreFieldsObject(fieldsObjectPointer, context5)

        val (context7, nameMemoryIndex) = context6.addSizedStaticUtf8String(shapeType.name.value)
        val context8 = compileStoreName(nameMemoryIndex, context7)

        return context8.addInstruction(metaTypePointer.get())
    }

    private fun compileConstructor(
        context: WasmFunctionContext,
    ): Pair<WasmFunctionContext, WasmConstValue.TableEntryIndex> {
        fun fieldParamIdentifier(field: Field) = "param_${field.name.value}"

        val fields = shapeType.fields.values
        val constructorName = shapeType.name.value
        val layout = WasmObjects.shapeLayout(shapeType)

        return WasmClosures.compileFunction(
            // TODO: uniquify name properly
            functionName = constructorName + freshNodeId(),
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
                    objectPointer = obj.get(),
                    layout = layout,
                    fieldValues = fields.map { field ->
                        field.name to Wasm.I.localGet(
                            fieldParamIdentifier(field)
                        )
                    },
                    context = constructorContext2,
                )
                constructorContext3.addInstruction(obj.get())
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
                address = metaTypePointer.get(),
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
                    address = metaTypePointer.get(),
                    offset = metaTypeLayout.tagValueOffset,
                    value = Wasm.I.i32Const(WasmConstValue.TagValue(tagValue)),
                )
            )
        }
    }

    private fun compileCreateFieldsObject(context: WasmFunctionContext): Pair<WasmFunctionContext, WasmLocalRef> {
        val fieldsType = metaType.fieldType(Identifier("fields")) as ShapeType
        val fieldsObjectLayout = WasmObjects.shapeLayout(fieldsType)
        val (context2, fieldsObjectPointer) = malloc("fields", fieldsObjectLayout, context)

        val context3 = shapeType.fields.values.fold(context2) { currentContext, field ->
            val (currentContext2, fieldObjectPointer) = compileCreateFieldObject(
                field = field,
                fieldObjectLayout = WasmObjects.shapeLayout(fieldsType.fieldType(field.name) as ShapeType),
                context = currentContext
            )
            compileStoreFieldObject(
                fieldsObjectPointer = fieldsObjectPointer,
                fieldsObjectLayout = fieldsObjectLayout,
                fieldName = field.name,
                fieldValue = fieldObjectPointer,
                context = currentContext2
            )
        }

        return Pair(context3, fieldsObjectPointer)
    }

    private fun compileCreateFieldObject(
        field: Field,
        fieldObjectLayout: WasmObjects.ShapeLayout,
        context: WasmFunctionContext,
    ): Pair<WasmFunctionContext, WasmLocalRef> {
        val (context2, fieldObjectPointer) = malloc(field.name.value, fieldObjectLayout, context)

        val (context3, nameMemoryIndex) = context2.addSizedStaticUtf8String(field.name.value)
        val context4 = context3.addInstruction(
            Wasm.I.i32Store(
                address = fieldObjectPointer.get(),
                offset = fieldObjectLayout.fieldOffset(Identifier("name")),
                value = Wasm.I.i32Const(nameMemoryIndex),
            )
        )

        val compiler = WasmCompiler(Image.EMPTY, ModuleSet(listOf()))

        val defineGetInstruction = defineShapeFieldGet(shapeType = shapeType, fieldName = field.name)
        val (context5, getPointer) = compiler.compileCreateFunction(defineGetInstruction, context4)
        val context6 = context5.addInstruction(
            Wasm.I.i32Store(
                address = fieldObjectPointer.get(),
                offset = fieldObjectLayout.fieldOffset(Identifier("get")),
                value = getPointer.get(),
            )
        )

        // TODO: update function

        return Pair(context6, fieldObjectPointer)
    }

    private fun compileStoreFieldObject(
        fieldsObjectPointer: WasmLocalRef,
        fieldsObjectLayout: WasmObjects.ShapeLayout,
        fieldName: Identifier,
        fieldValue: WasmLocalRef,
        context: WasmFunctionContext,
    ): WasmFunctionContext {
        return context.addInstruction(
            Wasm.I.i32Store(
                address = fieldsObjectPointer.get(),
                offset = fieldsObjectLayout.fieldOffset(fieldName),
                value = fieldValue.get(),
            )
        )
    }

    private fun compileStoreFieldsObject(fieldsObjectPointer: WasmLocalRef, context: WasmFunctionContext): WasmFunctionContext {
        return context.addInstruction(
            Wasm.I.i32Store(
                address = metaTypePointer.get(),
                offset = metaTypeLayout.fieldOffset(Identifier("fields")),
                value = fieldsObjectPointer.get(),
            )
        )
    }

    private fun compileStoreName(
        nameMemoryIndex: WasmDataSegmentKey,
        context: WasmFunctionContext
    ): WasmFunctionContext {
        return context.addInstruction(
            Wasm.I.i32Store(
                address = metaTypePointer.get(),
                offset = metaTypeLayout.fieldOffset(Identifier("name")),
                value = Wasm.I.i32Const(nameMemoryIndex),
            )
        )
    }
}
