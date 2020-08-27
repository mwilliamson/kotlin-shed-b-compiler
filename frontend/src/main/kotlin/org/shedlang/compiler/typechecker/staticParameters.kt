package org.shedlang.compiler.typechecker

import org.shedlang.compiler.ast.EffectParameterNode
import org.shedlang.compiler.ast.StaticParameterNode
import org.shedlang.compiler.ast.TypeParameterNode
import org.shedlang.compiler.types.EffectParameter
import org.shedlang.compiler.types.StaticParameter
import org.shedlang.compiler.types.StaticValueType
import org.shedlang.compiler.types.TypeParameter

internal fun typeCheckStaticParameters(
    parameters: List<StaticParameterNode>,
    context: TypeContext
): List<StaticParameter> {
    return parameters.map({ parameter ->
        typeCheckStaticParameter(parameter, context)
    })
}

private fun typeCheckStaticParameter(
    node: StaticParameterNode,
    context: TypeContext
): StaticParameter {
    return node.accept(object: StaticParameterNode.Visitor<StaticParameter> {
        override fun visit(node: TypeParameterNode): StaticParameter {
            return typeCheckTypeParameter(node, context)
        }

        override fun visit(node: EffectParameterNode): StaticParameter {
            val parameter = EffectParameter(name = node.name)
            context.addVariableType(node, StaticValueType(parameter))
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
        shapeId = null
    )
    context.addVariableType(parameter, StaticValueType(typeParameter))
    return typeParameter
}
