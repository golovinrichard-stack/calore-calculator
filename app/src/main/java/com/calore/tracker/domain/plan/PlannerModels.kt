package com.calore.tracker.domain.plan

import com.calore.tracker.domain.Allergen
import com.calore.tracker.domain.AnimalFlag
import com.calore.tracker.domain.Food
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.PantryLot
import com.calore.tracker.domain.PlanSlot
import com.calore.tracker.domain.Recipe
import com.calore.tracker.domain.RecipeCosts
import com.calore.tracker.domain.RecipeIngredient
import com.calore.tracker.domain.RecipeNutrition
import com.calore.tracker.domain.RecipeStep
import com.calore.tracker.domain.Recipes
import com.calore.tracker.domain.ShopSection
import com.calore.tracker.domain.SlotKind
import java.time.DayOfWeek
import java.time.LocalDate

/** All weights and limits of the planner (algorithms A15), in one place. Every value is *tunable*. */
data class PlannerConfig(
    /** Rated 👍 by the person. */
    val ratedUp: Double = 20.0,
    /** Each liked food in the ingredients … */
    val likedFood: Double = 10.0,
    /** … at most this much. */
    val likedFoodMax: Double = 20.0,
    /** The recipe's cuisine is one of the person's cuisines. */
    val cuisine: Double = 10.0,
    /** Used in the last [recentDays] days: −this × (variety − 1). */
    val recentPerVariety: Double = 15.0,
    val recentDays: Long = 7,
    /** How close the recipe's protein share of calories is to the person's target share. */
    val proteinFitMax: Double = 15.0,
    /** Cost of the meal against the budget per meal (only with a budget). */
    val costMax: Double = 15.0,
    /** At or under this share of the budget per meal: all [costMax] points … */
    val cheapRatio: Double = 0.5,
    /** … at or over this share: none; in between, linear. */
    val expensiveRatio: Double = 1.5,
    /** Uses pantry food that expires within [expiringWithinDays] days, by share of the recipe's grams. */
    val pantryMax: Double = 20.0,
    val expiringWithinDays: Long = 4,
    /** The cycle phase bonus (A20, M7) is capped at this. */
    val phaseMax: Double = 10.0,
    /** Freezable, when the box will be eaten after the default fridge days. */
    val freezable: Double = 10.0,
    /** Improvement rounds (A15 step 3.2). */
    val iterations: Int = 200,
    /** A day whose planned protein is more than this far off the target … */
    val proteinTolerance: Double = 0.10,
    /** … costs this much per person and day. */
    val proteinDayPenalty: Double = 20.0,
    /** A cooking session longer than the person's minutes per session costs this much. */
    val sessionMinutesPenalty: Double = 30.0,
    /** Regenerate: the last proposal's recipe for a meal costs this much, so others get a turn. */
    val regeneratePenalty: Double = 15.0,
)

/** The cycle phase bonus (A20). Returns 0 until M7 fills it in. */
fun interface PhaseBonusProvider {
    /** 0 to [PlannerConfig.phaseMax]: how well [recipe] suits [memberId]'s cycle phase on [date]. */
    fun bonus(memberId: String, date: LocalDate, recipe: PlannerRecipe): Double

    companion object {
        val NONE = PhaseBonusProvider { _, _, _ -> 0.0 }
    }
}

/** One ingredient as the planner sees it: the food's uuid, key and shop section, and its raw grams. */
data class PlannerIngredient(
    val foodUuid: String,
    val key: String?,
    val section: ShopSection,
    val grams: Double,
    val optional: Boolean = false,
)

/** A recipe with everything the planner needs. */
data class PlannerRecipe(
    val recipe: Recipe,
    val nutrition: RecipeNutrition,
    /** Optional ingredients included: they count for allergens, diet and disliked foods. */
    val ingredients: List<PlannerIngredient>,
    val allergens: Set<Allergen>,
    val animalFlags: Set<AnimalFlag>,
    /** The whole pot's cost in haléře, only when every ingredient has a price (A18). */
    val costHalers: Double? = null,
    /** Minutes of the steps that need the cook (not oven or simmering). */
    val activeMinutes: Int = 0,
) {
    val id: String get() = recipe.id

    /** Meals the recipe is for; a recipe that doesn't say is for lunch and dinner. */
    val meals: Set<MealType> get() = recipe.mealTypes.ifEmpty { DEFAULT_MEALS }

    /** Protein's share of the calories, 0–1; null when unknown. */
    val proteinShare: Double?
        get() = nutrition.total.protein?.takeIf { nutrition.total.kcal > 0 }?.let { 4 * it / nutrition.total.kcal }

    /** Protein grams per kcal, for daily protein totals. */
    val proteinPerKcal: Double
        get() = if (nutrition.total.kcal > 0) (nutrition.total.protein ?: 0.0) / nutrition.total.kcal else 0.0

    /** Cost in haléře per kcal, when known. */
    val costPerKcal: Double? get() = costHalers?.takeIf { nutrition.total.kcal > 0 }?.let { it / nutrition.total.kcal }

    companion object {
        val DEFAULT_MEALS = setOf(MealType.LUNCH, MealType.DINNER)

        /** Builds the planner's view of a recipe; [prices] are prices per gram by food (A18). */
        fun of(
            recipe: Recipe,
            ingredients: List<RecipeIngredient>,
            foods: Map<String, Food>,
            steps: List<RecipeStep>,
            prices: Map<String, Double> = emptyMap(),
        ): PlannerRecipe {
            val nutrition = Recipes.nutrition(ingredients, foods, recipe.servings, recipe.learnedYield)
            val cost = RecipeCosts.cost(ingredients, nutrition, recipe.servings, prices)
            return PlannerRecipe(
                recipe = recipe,
                nutrition = nutrition,
                ingredients = ingredients.map { i ->
                    val food = foods[i.foodUuid]
                    PlannerIngredient(i.foodUuid, food?.key, food?.section ?: ShopSection.OTHER, i.grams, i.optional)
                },
                allergens = Recipes.allergens(ingredients, foods),
                animalFlags = Recipes.animalFlags(ingredients, foods),
                costHalers = cost.totalHalers.takeIf { cost.complete },
                activeMinutes = activeMinutes(steps),
            )
        }

        fun activeMinutes(steps: List<RecipeStep>): Int = steps.filter { !it.passive }.sumOf { it.minutes ?: 0 }
    }
}

/** A person to plan for, with their 👍 / 👎 by recipe id. */
data class PlannerMember(val member: Member, val ratings: Map<String, Int> = emptyMap()) {
    val id: String get() = member.id
}

/** One meal of one person on one day. */
data class SlotKey(val date: LocalDate, val meal: MealType, val memberId: String)

val PlanSlot.key: SlotKey get() = SlotKey(date, meal, memberId)

/** Everything "Plan my week" plans from (A15 input). */
data class PlannerInput(
    /** The Monday of the week. */
    val weekStart: LocalDate,
    /** Nothing before this day is planned. */
    val today: LocalDate,
    /** The people to plan for; the first one is the cook (the phone's user). */
    val members: List<PlannerMember>,
    val recipes: List<PlannerRecipe>,
    val cookingDays: List<DayOfWeek>,
    /** Meals that come from prep boxes; the others are cooked fresh. */
    val prepped: Set<MealType>,
    val minutesPerSession: Int,
    /** The cook's variety (1–3): how many recipes per session and meal. */
    val variety: Int,
    val weeklyBudgetHalers: Long? = null,
    val pantry: List<PantryLot> = emptyList(),
    /** Slots that stay as they are: locked, eaten, with a box, eat out, free. */
    val kept: List<PlanSlot> = emptyList(),
    /** Recipes each person had in the [PlannerConfig.recentDays] days before the week: member id → recipe ids. */
    val recent: Map<String, Set<String>> = emptyMap(),
    val phase: PhaseBonusProvider = PhaseBonusProvider.NONE,
    val config: PlannerConfig = PlannerConfig(),
)

/** Why a recipe was chosen, for the proposal screen. */
sealed interface PlanReason {
    data object RatedUp : PlanReason
    data class LikedFoods(val keys: List<String>) : PlanReason
    data class Cuisine(val key: String) : PlanReason
    data object GoodProtein : PlanReason
    data object Cheap : PlanReason
    data class UsesPantry(val foodUuids: List<String>) : PlanReason
    data object CyclePhase : PlanReason
    data object Freezes : PlanReason
}

/** One recipe for a group of meals: one pot for these people and days. */
data class PlanChoice(
    val recipeId: String,
    val kind: SlotKind,
    /** The cooking day: the session for boxes, the day itself for cooking fresh. */
    val cookDate: LocalDate,
    val slots: List<SlotKey>,
    val score: Double,
    /** The main reasons, strongest first (at most 3). */
    val reasons: List<PlanReason>,
)

/** A candidate for one meal, for **Swap**. */
data class PlanAlternative(val recipeId: String, val score: Double, val reasons: List<PlanReason>)

/** The planner's proposal. */
data class PlanResult(
    val seed: Long,
    /** The kept slots and the new ones. New slots have empty ids; saving gives them ids. */
    val slots: List<PlanSlot>,
    val choices: List<PlanChoice>,
    /** Meals no recipe fits (nothing passes the filters). */
    val unplanned: List<SlotKey>,
    /** The week's total score (A15 step 3.2). */
    val total: Double,
) {
    val newKeys: Set<SlotKey> get() = choices.flatMap { it.slots }.toSet()
}
