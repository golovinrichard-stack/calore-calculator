package com.calore.tracker.data.household

import com.calore.tracker.domain.Allergen
import com.calore.tracker.domain.Diet
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.NutritionGoals
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** What a person shares with the household only if they allow it (M11.9): daily targets, meal split, diet, allergens. */
@Serializable
data class SharedTargets(
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val mealSplit: Map<String, Int>,
    val diet: String,
    val allergens: List<String>,
)

object SharedTargetsCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(member: Member): String {
        val t = member.effectiveTargets
        return json.encodeToString(
            SharedTargets.serializer(),
            SharedTargets(
                kcal = t.kcal,
                proteinG = t.proteinG,
                carbsG = t.carbsG,
                fatG = t.fatG,
                mealSplit = member.mealSplit.mapKeys { it.key.name },
                diet = member.diet.name,
                allergens = member.allergens.map { it.name }.sorted(),
            ),
        )
    }

    fun decode(text: String): SharedTargets? = try {
        json.decodeFromString(SharedTargets.serializer(), text)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    /** [member] with [targets], or with nothing shared when [targets] is null (they stopped sharing). */
    fun applyTo(member: Member, targets: SharedTargets?): Member {
        if (targets == null) {
            return member.copy(targets = null, mealSplit = Member.DEFAULT_MEAL_SPLIT, diet = Diet.OMNIVORE, allergens = emptySet())
        }
        val split = targets.mealSplit.mapNotNull { (k, v) -> MealType.entries.firstOrNull { it.name == k }?.let { it to v } }.toMap()
        return member.copy(
            targets = NutritionGoals(targets.kcal, targets.proteinG, targets.carbsG, targets.fatG),
            mealSplit = split.ifEmpty { Member.DEFAULT_MEAL_SPLIT },
            diet = Diet.entries.firstOrNull { it.name == targets.diet } ?: Diet.OMNIVORE,
            allergens = targets.allergens.mapNotNull { a -> Allergen.entries.firstOrNull { it.name == a } }.toSet(),
        )
    }
}
