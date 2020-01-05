package org.shedlang.compiler.tests.types

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.tests.shapeType
import org.shedlang.compiler.types.AnonymousUnionType
import org.shedlang.compiler.types.Tag
import org.shedlang.compiler.types.TagValue

class AnonymousUnionTests {
    @Test
    fun anonymousUnionShortDescriptionContainsAllMembers() {
        val tag = Tag(listOf(Identifier("Options")), Identifier("Option"))
        val noneType = shapeType(name = "None", tagValue = TagValue(tag, Identifier("None")))
        val someTag = shapeType(name = "Some", tagValue = TagValue(tag, Identifier("Some")))

        val type = AnonymousUnionType(
            tag = tag,
            members = listOf(noneType, someTag)
        )

        assertThat(type.shortDescription, equalTo("None | Some"))
    }
}
