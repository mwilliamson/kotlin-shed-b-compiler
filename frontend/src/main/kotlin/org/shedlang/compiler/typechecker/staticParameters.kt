
import org.shedlang.compiler.ast.EffectParameterNode
import org.shedlang.compiler.ast.StaticParameterNode
import org.shedlang.compiler.ast.TypeParameterNode
import org.shedlang.compiler.typechecker.TypeContext
import org.shedlang.compiler.types.*

internal fun typeCheckStaticParameters(
    parameters: List<StaticParameterNode>,
    context: TypeContext
): List<StaticParameter> {
    return parameters.map({ parameter ->
        typeCheckStaticParameter(parameter, context)
    })
}

internal fun typeCheckTypeParameters(parameters: List<TypeParameterNode>, context: TypeContext): List<TypeParameter> {
    return parameters.map({ parameter ->
        typeCheckTypeParameter(parameter, context)
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
            context.addVariableType(node, EffectType(parameter))
            return parameter
        }
    })
}

private fun typeCheckTypeParameter(
    parameter: TypeParameterNode,
    context: TypeContext
): TypeParameter {
    val typeParameter = TypeParameter(name = parameter.name, variance = parameter.variance)
    context.addVariableType(parameter, MetaType(typeParameter))
    return typeParameter
}
