package org.shedlang.compiler.tests

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.shedlang.compiler.ast.Identifier
import org.shedlang.compiler.types.*

class DiscriminatorTests {
    @Test
    fun whenTargetTypeIsNotShapeTypeThenDiscriminatorIsNotFound() {
        val sourceType = unionType(members = listOf(IntType))
        val targetType = IntType

        val discriminator = findDiscriminator(sourceType = sourceType, targetType = targetType)

        assertThat(discriminator, absent())
    }

    @Test
    fun whenTargetTypeIsMissingFieldWithSymbolTypeThenDiscriminatorIsNotFound() {
        val member1 = shapeType(name = "Member1", fields = listOf(
            field(name = "tag", type = IntType)
        ))
        val member2 = shapeType(name = "Member2",fields = listOf(
            field(name = "tag", type = IntType)
        ))
        val union = unionType("Union", members = listOf(member1, member2))

        val discriminator = findDiscriminator(sourceType = union, targetType = member1)

        assertThat(discriminator, absent())
    }

    @Test
    @Disabled
    fun whenSourceTypeIncludesTargetTypeWithNonUniqueTagThenDiscriminatorIsNotFound() {
        val member1 = shapeType(name = "Member1", fields = listOf(
            field(name = "tag", type = SymbolType(listOf(), "@Member"))
        ))
        val member2 = shapeType(name = "Member2",fields = listOf(
            field(name = "tag", type = SymbolType(listOf(), "@Member"))
        ))
        val union = unionType("Union", members = listOf(member1, member2))

        val discriminator = findDiscriminator(sourceType = union, targetType = member1)

        assertThat(discriminator, absent())
    }

    @Test
    fun whenSourceTypeIncludesTargetTypeWithUniqueTagThenDiscriminatorIsFound() {
        val member1 = shapeType(name = "Member1", fields = listOf(
            field(name = "tag", type = SymbolType(listOf(), "@Member1"))
        ))
        val member2 = shapeType(name = "Member2",fields = listOf(
            field(name = "tag", type = SymbolType(listOf(), "@Member2"))
        ))
        val union = unionType("Union", members = listOf(member1, member2))

        val discriminator = findDiscriminator(sourceType = union, targetType = member1)

        assertThat(discriminator, present(equalTo(Discriminator(
            fieldName = Identifier("tag"),
            symbolType = SymbolType(listOf(), "@Member1")
        ))))
    }

    @Test
    fun whenTargetTypeDoesNotHaveDiscriminatingFieldThenDiscriminatorIsNotFound() {
        val targetType = shapeType(name = "Target", fields = listOf(
            field(name = "tag", type = SymbolType(listOf(), "@Target"))
        ))

        val discriminator = findDiscriminator(sourceType = AnyType, targetType = targetType)

        assertThat(discriminator, absent())
    }
}
