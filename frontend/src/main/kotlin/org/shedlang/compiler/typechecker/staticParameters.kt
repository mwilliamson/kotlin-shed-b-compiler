import org.shedlang.compiler.ast.StaticParameterNode
import org.shedlang.compiler.ast.TypeParameterNode
import org.shedlang.compiler.typechecker.TypeContext
import org.shedlang.compiler.types.MetaType
import org.shedlang.compiler.types.StaticParameter
import org.shedlang.compiler.types.TypeParameter

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
    parameter: StaticParameterNode,
    context: TypeContext
): StaticParameter {
    return parameter.accept(object: StaticParameterNode.Visitor<StaticParameter> {
        override fun visit(node: TypeParameterNode): StaticParameter {
            return typeCheckTypeParameter(node, context)
        }
    })
}

private fun typeCheckTypeParameter(
    parameter: TypeParameterNode,
    context: TypeContext
): TypeParameter {
    val typeParameter = TypeParameter(name = parameter.name, variance = parameter.variance)
    context.addType(parameter, MetaType(typeParameter))
    return typeParameter
}
