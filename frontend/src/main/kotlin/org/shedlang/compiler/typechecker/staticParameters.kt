package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.EffectParameterNode
import org.shedlang.compiler.ast.TypeLevelParameterNode
import org.shedlang.compiler.ast.TypeParameterNode
import org.shedlang.compiler.types.EffectParameter
import org.shedlang.compiler.types.TypeLevelParameter
import org.shedlang.compiler.types.TypeLevelValueType
import org.shedlang.compiler.types.TypeParameter

internal fun typeCheckTypeLevelParameters(
    parameters: List<TypeLevelParameterNode>,
    context: TypeContext
): List<TypeLevelParameter> {
    return parameters.map({ parameter ->
        typeCheckTypeLevelParameter(parameter, context)
    })
}

private fun typeCheckTypeLevelParameter(
    node: TypeLevelParameterNode,
    context: TypeContext
): TypeLevelParameter {
    return node.accept(object: TypeLevelParameterNode.Visitor<TypeLevelParameter> {
        override fun visit(node: TypeParameterNode): TypeLevelParameter {
            return typeCheckTypeParameter(node, context)
        }

        override fun visit(node: EffectParameterNode): TypeLevelParameter {
            val parameter = EffectParameter(name = node.name, source = node.source)
            context.addVariableType(node, TypeLevelValueType(parameter))
            return parameter
        }
    })
}

private fun typeCheckTypeParameter(
    parameter: TypeParameterNode,
    context: TypeContext
): TypeParameter {
    val typeParameter = TypeParameter(
        name = parameter.name,
        variance = parameter.variance,
        shapeId = null,
        source = parameter.source,
    )
    context.addVariableType(parameter, TypeLevelValueType(typeParameter))
    return typeParameter
}
