package com.calore.tracker.domain

import java.time.DayOfWeek
import java.time.LocalDate

/** Cut, maintain, build, or recomp (lose fat and build muscle at maintenance with high protein). */
enum class GoalType(
    /** Speeds offered, in % of body weight per week (algorithms A3). */
    val speedsPct: List<Double>,
    /** Default protein in g per kg of base weight (A4). */
    val defaultProteinPerKg: Double,
) {
    CUT(listOf(0.5, 0.75, 1.0), 2.0),
    MAINTAIN(emptyList(), 1.6),
    BUILD(listOf(0.25, 0.5), 1.8),
    RECOMP(emptyList(), 2.0),
    ;

    /** -1 for a cut, +1 for a build, 0 otherwise. */
    val direction: Int
        get() = when (this) {
            CUT -> -1
            BUILD -> 1
            MAINTAIN, RECOMP -> 0
        }

    val hasSpeed: Boolean get() = speedsPct.isNotEmpty()

    val defaultSpeedPct: Double? get() = speedsPct.firstOrNull()
}

/** `AUTO`: protein per kg, fat %, carbs fill the rest (A4). `SPLIT`: the manual % split. */
enum class MacroMode { AUTO, SPLIT }

enum class Diet { OMNIVORE, VEGETARIAN, VEGAN, PESCATARIAN, NO_PORK }

/** The 14 allergens EU law requires on labels. */
enum class Allergen {
    GLUTEN, CRUSTACEANS, EGGS, FISH, PEANUTS, SOY, MILK, NUTS, CELERY, MUSTARD, SESAME, SULPHITES, LUPIN, MOLLUSCS,
}

enum class Equipment { OVEN, AIR_FRYER, SLOW_COOKER, RICE_COOKER, MICROWAVE, BLENDER }

/** `ME` is the phone's user; `MANAGED` someone without the app; `HOUSEHOLD` another phone (M11). */
enum class MemberKind { ME, MANAGED, HOUSEHOLD }

/**
 * A person the app keeps targets for: the phone's user, or someone they cook for.
 * Fields that are only needed by later features stay null or default until the user fills them in.
 */
data class Member(
    val id: String,
    val kind: MemberKind,
    val name: String,
    /** ARGB, for name labels on boxes. */
    val colour: Int,
    val sex: Sex? = null,
    val birthDate: LocalDate? = null,
    val heightCm: Double? = null,
    /** Only for [MemberKind.MANAGED]; the phone's user keeps using the weight log. */
    val weightKg: Double? = null,
    val activity: ActivityLevel? = null,
    val goalType: GoalType? = null,
    /** % of body weight per week, positive; the direction comes from [goalType]. */
    val goalRatePct: Double? = null,
    val macroMode: MacroMode = MacroMode.AUTO,
    val proteinPerKg: Double? = null,
    val fatPct: Int? = null,
    val split: MacroSplit? = null,
    /** Current daily targets; null means the defaults. */
    val targets: NutritionGoals? = null,
    val mealSplit: Map<MealType, Int> = DEFAULT_MEAL_SPLIT,
    val diet: Diet = Diet.OMNIVORE,
    val allergens: Set<Allergen> = emptySet(),
    /** Food keys (`generic:…`) or shop section names (M2). */
    val likedFoods: List<String> = emptyList(),
    val dislikedFoods: List<String> = emptyList(),
    val cuisines: List<String> = emptyList(),
    /** Empty means "not set": don't filter by equipment. */
    val equipment: Set<Equipment> = emptySet(),
    /** 1 = the same meals all week is fine, 2 = some variety, 3 = different every day. */
    val variety: Int = 2,
    val weeklyBudget: Money? = null,
    val cookSessionsPerWeek: Int = 2,
    /** Empty means the app chooses. */
    val cookDays: List<DayOfWeek> = emptyList(),
    val minutesPerSession: Int = 120,
    val preppedMeals: Set<MealType> = setOf(MealType.LUNCH, MealType.DINNER),
    /** Used in M11: may other phones see this person's meal targets? */
    val sharesTargets: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    /** The targets to track against today. */
    val effectiveTargets: NutritionGoals get() = targets ?: NutritionGoals.DEFAULT

    /** Protein per kg to use: the member's choice, or the default for the goal (A4). */
    val effectiveProteinPerKg: Double
        get() = proteinPerKg ?: (goalType ?: GoalType.MAINTAIN).defaultProteinPerKg

    val effectiveFatPct: Int get() = fatPct ?: DEFAULT_FAT_PCT

    val effectiveSplit: MacroSplit get() = split?.takeIf { it.isValid } ?: MacroSplit.DEFAULT

    companion object {
        val DEFAULT_MEAL_SPLIT: Map<MealType, Int> =
            mapOf(MealType.BREAKFAST to 20, MealType.LUNCH to 30, MealType.DINNER to 30, MealType.SNACK to 20)

        const val DEFAULT_COLOUR: Int = 0xFF2E7D32.toInt()

        /** Fat as % of calories in AUTO macros (A4). */
        const val DEFAULT_FAT_PCT = 25

        /** The phone's user before they have told the app anything. */
        fun newMe(id: String, name: String, now: Long) = Member(
            id = id,
            kind = MemberKind.ME,
            name = name,
            colour = DEFAULT_COLOUR,
            createdAt = now,
            updatedAt = now,
        )
    }
}

/** How a body-fat value was found (A5). */
enum class BodyFatMethod { NAVY, MANUAL }

/** One tape measurement or typed-in body-fat value. Private: never shared with the household. */
data class BodyMeasurement(
    val id: String,
    val memberId: String,
    val date: LocalDate,
    val neckCm: Double? = null,
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val bodyFatPct: Double?,
    val method: BodyFatMethod,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** One weekly check-in (A7). Private. */
data class Checkin(
    val id: String,
    val memberId: String,
    /** The Monday of the week it was made. */
    val weekStart: LocalDate,
    val trendStartKg: Double?,
    val trendEndKg: Double?,
    val avgIntakeKcal: Double?,
    val countedDays: Int,
    val estimatedMaintenanceKcal: Int?,
    val oldTargetKcal: Int,
    val proposedTargetKcal: Int,
    val accepted: Boolean,
    val createdAt: Long,
)

/** Shop aisles; also used for liked and disliked kinds of food until M2 adds single foods. */
enum class ShopSection {
    VEGETABLES, FRUIT, MEAT, FISH, DAIRY, EGGS, BAKERY, GRAINS, LEGUMES, CANNED, FROZEN, SPICES_SAUCES, OILS,
    SNACKS, DRINKS, SUPPLEMENTS, OTHER,
    ;

    companion object {
        /** The sections that make sense as "foods I like / don't like". */
        val FOOD_KINDS = listOf(VEGETABLES, FRUIT, MEAT, FISH, DAIRY, EGGS, GRAINS, LEGUMES)
    }
}

/** What a food contains, for diet filters (vegetarian, vegan, no pork …). */
enum class AnimalFlag { DAIRY, EGG, FISH, SHELLFISH, MEAT, PORK }

/** Where food is kept. */
enum class Storage { FRIDGE, FREEZER, CUPBOARD }

/** What a diary entry is. */
enum class EntryType { FOOD, QUICK_ADD, DISH, BOX, SUPPLEMENT }

/** Cuisine keys stored in `members.cuisines` (lower case, stable). */
object Cuisines {
    val ALL = listOf("czech", "italian", "mediterranean", "asian", "indian", "mexican", "middle_eastern", "american")
}
