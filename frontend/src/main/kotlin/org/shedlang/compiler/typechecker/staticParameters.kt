import org.shedlang.compiler.ast.TypeParameterNode
import org.shedlang.compiler.typechecker.TypeContext
import org.shedlang.compiler.types.MetaType
import org.shedlang.compiler.types.TypeParameter

internal fun typeCheckTypeParameters(parameters: List<TypeParameterNode>, context: TypeContext): List<TypeParameter> {
    return parameters.map({ parameter ->
        val typeParameter = TypeParameter(name = parameter.name, variance = parameter.variance)
        context.addType(parameter, MetaType(typeParameter))
        typeParameter
    })
}
