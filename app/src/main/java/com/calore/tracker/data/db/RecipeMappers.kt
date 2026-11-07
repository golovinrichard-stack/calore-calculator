package com.calore.tracker.data.db

import com.calore.tracker.domain.Recipe
import com.calore.tracker.domain.RecipeIngredient
import com.calore.tracker.domain.RecipeStep
import com.calore.tracker.domain.Visibility

internal fun RecipeEntity.toDomain(): Recipe = Recipe(
    id = id,
    key = key,
    name = name,
    nameCs = nameCs,
    nameEn = nameEn,
    userEdited = userEdited,
    shortCode = shortCode,
    servings = servings,
    mealTypes = mealTypes.toSet(),
    cuisine = cuisine,
    tags = tags,
    equipment = equipment.toSet(),
    fridgeDays = fridgeDays,
    freezable = freezable,
    freezerMonths = freezerMonths,
    reheatNote = reheatNote,
    mealPrepFriendly = mealPrepFriendly,
    learnedYield = learnedYield,
    learnedYieldCount = learnedYieldCount,
    ownerMemberId = ownerMemberId,
    visibility = JsonColumns.enumByName<Visibility>(visibility) ?: Visibility.SHARED,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun Recipe.toEntity(deletedAt: Long? = null): RecipeEntity = RecipeEntity(
    id = id,
    key = key,
    name = name,
    nameCs = nameCs,
    nameEn = nameEn,
    userEdited = userEdited,
    shortCode = shortCode,
    servings = servings,
    // Stored in a fixed order so the JSON doesn't change when nothing changed.
    mealTypes = mealTypes.sortedBy { it.ordinal },
    cuisine = cuisine,
    tags = tags,
    equipment = equipment.sortedBy { it.ordinal },
    fridgeDays = fridgeDays,
    freezable = freezable,
    freezerMonths = freezerMonths,
    reheatNote = reheatNote,
    mealPrepFriendly = mealPrepFriendly,
    learnedYield = learnedYield,
    learnedYieldCount = learnedYieldCount,
    ownerMemberId = ownerMemberId,
    visibility = visibility.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun RecipeIngredientEntity.toDomain() = RecipeIngredient(id, recipeId, position, foodUuid, grams, note, optional)

internal fun RecipeIngredient.toEntity() = RecipeIngredientEntity(id, recipeId, position, foodUuid, grams, note, optional)

internal fun RecipeStepEntity.toDomain() = RecipeStep(id, recipeId, position, text, textCs, textEn, minutes, passive, equipment)

internal fun RecipeStep.toEntity() = RecipeStepEntity(id, recipeId, position, text, textCs, textEn, minutes, passive, equipment)
