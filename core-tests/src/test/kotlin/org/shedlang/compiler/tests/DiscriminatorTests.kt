package org.shedlang.compiler.tests

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import org.junit.jupiter.api.Test
import org.shedlang.compiler.types.*

class DiscriminatorTests {
    @Test
    fun whenSourceTypeIsNotUnionThenDiscriminatorIsNotFound() {
        val tag = tag(listOf("Example"), "Union")
        val member1 = shapeType(
            name = "Member1",
            tagValue = tagValue(tag, "Member1")
        )

        val discriminator = findDiscriminator(sourceType = AnyType, targetType = member1)

        assertThat(discriminator, absent())
    }

    @Test
    fun whenTargetTypeIsNotShapeTypeThenDiscriminatorIsNotFound() {
        val sourceType = unionType(members = listOf())
        val targetType = IntType

        val discriminator = findDiscriminator(sourceType = sourceType, targetType = targetType)

        assertThat(discriminator, absent())
    }

    @Test
    fun whenTargetTypeIsMissingTagValueThenDiscriminatorIsNotFound() {
        val member1 = shapeType(name = "Member", tagValue = null)
        val union = unionType("Union")

        val discriminator = findDiscriminator(sourceType = union, targetType = member1)

        assertThat(discriminator, absent())
    }

    @Test
    fun whenSourceTypeIncludesTargetTypeWithNonUniqueTagThenDiscriminatorIsNotFound() {
        val tag = tag(listOf("Example"), "Union")
        val member1 = shapeType(name = "Member1", tagValue = tagValue(tag, "Member"))
        val member2 = shapeType(name = "Member2", tagValue = tagValue(tag, "Member"))
        val union = unionType("Union", tag = tag, members = listOf(member1, member2))

        val discriminator = findDiscriminator(sourceType = union, targetType = member1)

        assertThat(discriminator, absent())
    }

    @Test
    fun whenSourceTypeIncludesTargetTypeWithUniqueTagThenDiscriminatorIsFound() {
        val tag = tag(listOf("Example"), "Union")
        val member1 = shapeType(name = "Member1", tagValue = tagValue(tag, "Member1"))
        val member2 = shapeType(name = "Member2", tagValue = tagValue(tag, "Member2"))
        val union = unionType("Union", tag = tag, members = listOf(member1, member2))

        val discriminator = findDiscriminator(sourceType = union, targetType = member1)

        assertThat(discriminator, present(isDiscriminator(
            tagValue = isTagValue(equalTo(tag), "Member1"),
            targetType = isType(member1)
        )))
    }

    @Test
    fun whenSourceTypeIncludesEquivalentTargetTypeWithUniqueTagThenDiscriminatorIsFound() {
        val tag = tag(listOf("Example"), "Union")

        val typeParameter = covariantTypeParameter("T")

        val member1 = parametrizedShapeType(
            name = "Member1",
            parameters = listOf(typeParameter),
            tagValue = tagValue(tag, "Member1"),
            fields = listOf(
                field(name = "value", type = typeParameter)
            )
        )
        val member2 = shapeType(name = "Member2", tagValue = tagValue(tag, "Member2"))
        val union = unionType("Union", tag = tag, members = listOf(applyStatic(member1, listOf(IntType)) as Type, member2))

        val discriminator = findDiscriminator(sourceType = union, targetType = applyStatic(member1, listOf(IntType)))

        assertThat(discriminator, present(isDiscriminator(
            tagValue = equalTo(tagValue(tag, "Member1")),
            targetType = isEquivalentType(applyStatic(member1, listOf(IntType)) as Type)
        )))
    }

    @Test
    fun whenSourceTypeIncludesParametrizedMemberWithCompatibleTypeParameterWithUniqueTagThenDiscriminatorIsFound() {
        val tag = tag(listOf("Example"), "Union")

        val typeParameter = covariantTypeParameter("T")

        val member1 = parametrizedShapeType(
            name = "Member1",
            parameters = listOf(typeParameter),
            tagValue = tagValue(tag, "Member1"),
            fields = listOf(
                field(name = "value", type = typeParameter)
            )
        )
        val member2 = shapeType(name = "Member2", tagValue = tagValue(tag, "Member2"))
        val union = unionType("Union", tag = tag, members = listOf(applyStatic(member1, listOf(IntType)) as Type, member2))

        val discriminator = findDiscriminator(sourceType = union, targetType = applyStatic(member1, listOf(AnyType)))

        assertThat(discriminator, present(isDiscriminator(
            tagValue = equalTo(tagValue(tag, "Member1")),
            targetType = isEquivalentType(applyStatic(member1, listOf(IntType)) as Type)
        )))
    }

    @Test
    fun whenSourceTypeIncludesParametrizedMemberWithIncompatibleTypeParameterWithUniqueTagThenDiscriminatorIsNotFound() {
        val tag = tag(listOf("Example"), "Union")

        val typeParameter = covariantTypeParameter("T")

        val member1 = parametrizedShapeType(
            name = "Member1",
            parameters = listOf(typeParameter),
            tagValue = tagValue(tag, "Member1"),
            fields = listOf(
                field(name = "value", type = typeParameter)
            )
        )
        val member2 = shapeType(name = "Member2", tagValue = tagValue(tag, "Member2"))
        val union = unionType("Union", tag = tag, members = listOf(applyStatic(member1, listOf(AnyType)) as Type, member2))

        val discriminator = findDiscriminator(sourceType = union, targetType = applyStatic(member1, listOf(IntType)))

        assertThat(discriminator, absent())
    }

    @Test
    fun whenSourceTypeIncludesParametrizedMemberAndTargetTypeIsCompatibleTypeFunctionWithoutParameterThenParameterIsInferred() {
        val tag = tag(listOf("Example"), "Union")

        val typeParameter = covariantTypeParameter("T")

        val member1 = parametrizedShapeType(
            name = "Member1",
            parameters = listOf(typeParameter),
            tagValue = tagValue(tag, "Member1"),
            fields = listOf(
                field(name = "value", type = typeParameter)
            )
        )
        val member2 = shapeType(name = "Member2", tagValue = tagValue(tag, "Member2"))
        val union = unionType("Union", tag = tag, members = listOf(applyStatic(member1, listOf(IntType)) as Type, member2))

        val discriminator = findDiscriminator(sourceType = union, targetType = member1)

        assertThat(discriminator, present(isDiscriminator(
            tagValue = equalTo(tagValue(tag, "Member1")),
            targetType = isEquivalentType(applyStatic(member1, listOf(IntType)) as Type)
        )))
    }

    @Test
    fun whenSourceTypeIncludesParametrizedMemberAndTargetTypeIsIncompatibleTypeFunctionWithoutParameterThenDiscriminatorIsNotFound() {
        val tag = tag(listOf("Example"), "Union")

        val typeParameter = covariantTypeParameter("T")

        val tagValue = tagValue(tag, "Member")
        val member1 = parametrizedShapeType(
            name = "Member1",
            parameters = listOf(typeParameter),
            tagValue = tagValue,
            fields = listOf(
                field(name = "value1", type = typeParameter)
            )
        )
        val member2 = parametrizedShapeType(
            name = "Member2",
            parameters = listOf(typeParameter),
            tagValue = tagValue,
            fields = listOf(
                field(name = "value2", type = typeParameter)
            )
        )
        val union = unionType("Union", tag = tag, members = listOf(applyStatic(member1, listOf(IntType)) as Type, applyStatic(member2, listOf(IntType)) as Type))

        val discriminator = findDiscriminator(sourceType = union, targetType = member1)

        assertThat(discriminator, absent())
    }
}
