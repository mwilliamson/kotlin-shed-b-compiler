package org.shedlang.compiler.tests

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.present
import org.junit.jupiter.api.Test
import org.shedlang.compiler.types.*

class DiscriminatorTests {
    private val discriminatorShapeId = freshShapeId()

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
            field(name = "tag", shapeId = discriminatorShapeId, type = IntType)
        ))
        val member2 = shapeType(name = "Member2",fields = listOf(
            field(name = "tag", shapeId = discriminatorShapeId, type = IntType)
        ))
        val union = unionType("Union", members = listOf(member1, member2))

        val discriminator = findDiscriminator(sourceType = union, targetType = member1)

        assertThat(discriminator, absent())
    }

    @Test
    fun whenSourceTypeMemberIsMissingDiscriminatorFieldThenDiscriminatorIsNotFound() {
        val member1 = shapeType(name = "Member1", fields = listOf(
            field(name = "tag", shapeId = discriminatorShapeId, type = symbolType(listOf(), "@Member1"))
        ))
        val member2 = shapeType(name = "Member2",fields = listOf())
        val union = unionType("Union", members = listOf(member1, member2))

        val discriminator = findDiscriminator(sourceType = union, targetType = member1)

        assertThat(discriminator, absent())
    }

    @Test
    fun whenSourceTypeMemberHasFieldNotOfSymbolTypeThenDiscriminatorIsNotFound() {
        val member1 = shapeType(name = "Member1", fields = listOf(
            field(name = "tag", shapeId = discriminatorShapeId, type = symbolType(listOf(), "@Member1"))
        ))
        val member2 = shapeType(name = "Member2",fields = listOf(
            field(name = "tag", shapeId = discriminatorShapeId, type = IntType)
        ))
        val union = unionType("Union", members = listOf(member1, member2))

        val discriminator = findDiscriminator(sourceType = union, targetType = member1)

        assertThat(discriminator, absent())
    }

    @Test
    fun whenSourceTypeMemberHasFieldOfAnySymbolTypeThenDiscriminatorIsNotFound() {
        val member1 = shapeType(name = "Member1", fields = listOf(
            field(name = "tag", shapeId = discriminatorShapeId, type = symbolType(listOf(), "@Member1"))
        ))
        val member2 = shapeType(name = "Member2",fields = listOf(
            field(name = "tag", shapeId = discriminatorShapeId, type = AnySymbolType)
        ))
        val union = unionType("Union", members = listOf(member1, member2))

        val discriminator = findDiscriminator(sourceType = union, targetType = member1)

        assertThat(discriminator, absent())
    }

    @Test
    fun whenSourceTypeIncludesTargetTypeWithNonUniqueTagThenDiscriminatorIsNotFound() {
        val member1 = shapeType(name = "Member1", fields = listOf(
            field(name = "tag", shapeId = discriminatorShapeId, type = symbolType(listOf(), "@Member"))
        ))
        val member2 = shapeType(name = "Member2",fields = listOf(
            field(name = "tag", shapeId = discriminatorShapeId, type = symbolType(listOf(), "@Member"))
        ))
        val union = unionType("Union", members = listOf(member1, member2))

        val discriminator = findDiscriminator(sourceType = union, targetType = member1)

        assertThat(discriminator, absent())
    }

    @Test
    fun discriminatorFieldsMustHaveSameShapeId() {
        val member1 = shapeType(name = "Member1", fields = listOf(
            field(name = "tag", shapeId = discriminatorShapeId, type = symbolType(listOf(), "@Member1"))
        ))
        val member2 = shapeType(name = "Member2",fields = listOf(
            field(name = "tag", shapeId = freshShapeId(), type = symbolType(listOf(), "@Member2"))
        ))
        val union = unionType("Union", members = listOf(member1, member2))

        val discriminator = findDiscriminator(sourceType = union, targetType = member1)

        assertThat(discriminator, absent())
    }

    @Test
    fun whenSourceTypeIncludesTargetTypeWithUniqueTagThenDiscriminatorIsFound() {
        val discriminatorField = field(name = "tag", shapeId = discriminatorShapeId, type = symbolType(listOf(), "@Member1"))
        val member1 = shapeType(name = "Member1", fields = listOf(
            discriminatorField
        ))
        val member2 = shapeType(name = "Member2",fields = listOf(
            field(name = "tag", shapeId = discriminatorShapeId, type = symbolType(listOf(), "@Member2"))
        ))
        val union = unionType("Union", members = listOf(member1, member2))

        val discriminator = findDiscriminator(sourceType = union, targetType = member1)

        assertThat(discriminator, present(allOf(
            has(Discriminator::field, equalTo(discriminatorField)),
            has(Discriminator::symbolType, equalTo(symbolType(listOf(), "@Member1")))
        )))
    }

    @Test
    fun whenSourceTypeIncludesEquivalentTargetTypeWithUniqueTagThenDiscriminatorIsFound() {
        val typeParameter = covariantTypeParameter("T")
        val discriminatorField = field(name = "tag", shapeId = discriminatorShapeId, type = symbolType(listOf(), "@Member1"))
        val member1 = parametrizedShapeType(
            name = "Member1",
            parameters = listOf(typeParameter),
            fields = listOf(
                discriminatorField,
                field(name = "value", type = typeParameter)
            )
        )
        val member2 = shapeType(name = "Member2",fields = listOf(
            field(name = "tag", shapeId = discriminatorShapeId, type = symbolType(listOf(), "@Member2"))
        ))
        val union = unionType("Union", members = listOf(applyStatic(member1, listOf(IntType)), member2))

        val discriminator = findDiscriminator(sourceType = union, targetType = applyStatic(member1, listOf(IntType)))

        assertThat(discriminator, present(allOf(
            has(Discriminator::field, equalTo(discriminatorField)),
            has(Discriminator::symbolType, equalTo(symbolType(listOf(), "@Member1")))
        )))
    }

    @Test
    fun whenSourceTypeIncludesParametrizedMemberWithCompatibleTypeParameterWithUniqueTagThenDiscriminatorIsFound() {
        val typeParameter = covariantTypeParameter("T")
        val discriminatorField = field(name = "tag", shapeId = discriminatorShapeId, type = symbolType(listOf(), "@Member1"))
        val member1 = parametrizedShapeType(
            name = "Member1",
            parameters = listOf(typeParameter),
            fields = listOf(
                discriminatorField,
                field(name = "value", type = typeParameter)
            )
        )
        val member2 = shapeType(name = "Member2",fields = listOf(
            field(name = "tag", shapeId = discriminatorShapeId, type = symbolType(listOf(), "@Member2"))
        ))
        val union = unionType("Union", members = listOf(applyStatic(member1, listOf(IntType)), member2))

        val discriminator = findDiscriminator(sourceType = union, targetType = applyStatic(member1, listOf(AnyType)))

        assertThat(discriminator, present(allOf(
            has(Discriminator::field, equalTo(discriminatorField)),
            has(Discriminator::symbolType, equalTo(symbolType(listOf(), "@Member1")))
        )))
    }

    @Test
    fun whenSourceTypeIncludesParametrizedMemberWithIncompatibleTypeParameterWithUniqueTagThenDiscriminatorIsNotFound() {
        val typeParameter = covariantTypeParameter("T")
        val member1 = parametrizedShapeType(
            name = "Member1",
            parameters = listOf(typeParameter),
            fields = listOf(
                field(name = "tag", shapeId = discriminatorShapeId, type = symbolType(listOf(), "@Member1")),
                field(name = "value", type = typeParameter)
            )
        )
        val member2 = shapeType(name = "Member2",fields = listOf(
            field(name = "tag", shapeId = discriminatorShapeId, type = symbolType(listOf(), "@Member2"))
        ))
        val union = unionType("Union", members = listOf(applyStatic(member1, listOf(AnyType)), member2))

        val discriminator = findDiscriminator(sourceType = union, targetType = applyStatic(member1, listOf(IntType)))

        assertThat(discriminator, absent())
    }

    @Test
    fun whenTargetTypeDoesNotHaveDiscriminatingFieldThenDiscriminatorIsNotFound() {
        val targetType = shapeType(name = "Target", fields = listOf(
            field(name = "tag", shapeId = discriminatorShapeId, type = symbolType(listOf(), "@Target"))
        ))

        val discriminator = findDiscriminator(sourceType = AnyType, targetType = targetType)

        assertThat(discriminator, absent())
    }
}
