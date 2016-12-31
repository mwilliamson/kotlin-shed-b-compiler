package org.shedlang.compiler.tests.typechecker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.cast
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.IntegerLiteralNode
import org.shedlang.compiler.ast.SourceLocation
import org.shedlang.compiler.typechecker.IntType
import org.shedlang.compiler.typechecker.TypeContext
import org.shedlang.compiler.typechecker.inferType

class TypeCheckExpressionTests {
    @Test
    fun integerLiteralIsTypedAsInteger() {
        val node = IntegerLiteralNode(42, anySourceLocation())
        val type = inferType(node, emptyTypeContext())
        assertThat(type, cast(equalTo(IntType)))
    }

    fun emptyTypeContext(): TypeContext {
        return TypeContext()
    }

    fun anySourceLocation(): SourceLocation {
        return SourceLocation("<string>", 0)
    }
}
