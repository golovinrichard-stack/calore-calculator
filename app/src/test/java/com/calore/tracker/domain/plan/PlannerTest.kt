package com.calore.tracker.domain.plan

import com.calore.tracker.domain.Allergen
import com.calore.tracker.domain.AnimalFlag
import com.calore.tracker.domain.Diet
import com.calore.tracker.domain.Equipment
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.MemberKind
import com.calore.tracker.domain.Nutrients
import com.calore.tracker.domain.NutritionGoals
import com.calore.tracker.domain.PantryLot
import com.calore.tracker.domain.PlanSlot
import com.calore.tracker.domain.Recipe
import com.calore.tracker.domain.RecipeNutrition
import com.calore.tracker.domain.ShopSection
import com.calore.tracker.domain.SlotKind
import com.calore.tracker.domain.mealTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

class PlannerTest {

    private val monday = LocalDate.of(2026, 11, 9)
    private val saturday = LocalDate.of(2026, 11, 7)
    private val lunchAndDinner = mapOf(MealType.LUNCH to 50, MealType.DINNER to 50)

    private fun person(
        id: String,
        diet: Diet = Diet.OMNIVORE,
        allergens: Set<Allergen> = emptySet(),
        variety: Int = 2,
        split: Map<MealType, Int> = lunchAndDinner,
        liked: List<String> = emptyList(),
        disliked: List<String> = emptyList(),
        equipment: Set<Equipment> = emptySet(),
        ratings: Map<String, Int> = emptyMap(),
    ) = PlannerMember(
        Member(
            id = id,
            kind = if (id == "me") MemberKind.ME else MemberKind.MANAGED,
            name = id,
            colour = 0,
            targets = NutritionGoals(kcal = 2000, proteinG = 150, carbsG = 200, fatG = 67),
            mealSplit = split,
            diet = diet,
            allergens = allergens,
            likedFoods = liked,
            dislikedFoods = disliked,
            equipment = equipment,
            variety = variety,
        ),
        ratings,
    )

    private fun recipe(
        id: String,
        meals: Set<MealType> = setOf(MealType.LUNCH, MealType.DINNER),
        kcal: Double = 2000.0,
        protein: Double = 150.0,
        flags: Set<AnimalFlag> = emptySet(),
        allergens: Set<Allergen> = emptySet(),
        foods: List<PlannerIngredient> = listOf(PlannerIngredient("rice-$id", "generic:rice", ShopSection.GRAINS, 500.0)),
        prep: Boolean = true,
        freezable: Boolean = true,
        fridgeDays: Int = 3,
        equipment: Set<Equipment> = emptySet(),
        cuisine: String? = null,
        costHalers: Double? = null,
        minutes: Int = 20,
    ) = PlannerRecipe(
        recipe = Recipe(
            id = id,
            name = id,
            shortCode = id.take(1).uppercase(),
            servings = 4,
            mealTypes = meals,
            cuisine = cuisine,
            equipment = equipment,
            fridgeDays = fridgeDays,
            freezable = freezable,
            mealPrepFriendly = prep,
        ),
        nutrition = Nutrients(kcal = kcal, protein = protein).let { RecipeNutrition(it, it.scaledBy(0.25), null, foods.sumOf { f -> f.grams }, 0) },
        ingredients = foods,
        allergens = allergens,
        animalFlags = flags,
        costHalers = costHalers,
        activeMinutes = minutes,
    )

    private fun input(
        members: List<PlannerMember>,
        recipes: List<PlannerRecipe>,
        variety: Int = 2,
        kept: List<PlanSlot> = emptyList(),
        pantry: List<PantryLot> = emptyList(),
        cookingDays: List<DayOfWeek> = listOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY),
        today: LocalDate = saturday,
        budget: Long? = null,
        minutes: Int = 120,
    ) = PlannerInput(
        weekStart = monday,
        today = today,
        members = members,
        recipes = recipes,
        cookingDays = cookingDays,
        prepped = setOf(MealType.LUNCH, MealType.DINNER),
        minutesPerSession = minutes,
        variety = variety,
        weeklyBudgetHalers = budget,
        pantry = pantry,
        kept = kept,
    )

    private fun PlanResult.recipeOf(key: SlotKey): String? = slots.firstOrNull { it.date == key.date && it.meal == key.meal && it.memberId == key.memberId }?.recipeId

    // --- A15 test cases ---

    @Test
    fun aVeganMemberNeverGetsARecipeWithAnAnimalFlag() {
        val recipes = listOf(
            recipe("chicken", flags = setOf(AnimalFlag.MEAT)),
            recipe("cheese", flags = setOf(AnimalFlag.DAIRY)),
            recipe("lentils"),
            recipe("tofu"),
        )
        val result = Planner.plan(input(listOf(person("me", diet = Diet.VEGAN)), recipes), seed = 1)
        val used = result.slots.mapNotNull { it.recipeId }.toSet()
        assertTrue(used.isNotEmpty())
        assertTrue(used.all { it == "lentils" || it == "tofu" })
    }

    @Test
    fun aMilkAllergenFiltersOutEveryRecipeWithAMilkIngredient() {
        val recipes = listOf(
            recipe("risotto", allergens = setOf(Allergen.MILK)),
            recipe("lasagne", allergens = setOf(Allergen.MILK, Allergen.GLUTEN)),
            recipe("curry"),
        )
        val me = person("me", allergens = setOf(Allergen.MILK))
        val result = Planner.plan(input(listOf(me), recipes), seed = 3)
        assertEquals(setOf("curry"), result.slots.mapNotNull { it.recipeId }.toSet())
        for (r in recipes) assertEquals(r.id == "curry", Planner.allowedForPerson(me, r))
    }

    @Test
    fun withVariety1AndTwoSessionsEachSessionHasOneLunchRecipe() {
        val recipes = (1..6).map { recipe("r$it") }
        val result = Planner.plan(input(listOf(person("me", variety = 1)), recipes, variety = 1), seed = 7)
        val lunches = result.choices.filter { it.kind == SlotKind.BOX && it.slots.all { s -> s.meal == MealType.LUNCH } }
        val sessions = lunches.groupBy { it.cookDate }
        // Mon–Wed lunches come from Sunday's session, Thu–Sun lunches from Wednesday's.
        assertEquals(setOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 11)), sessions.keys)
        for ((day, choices) in sessions) assertEquals("session $day", 1, choices.map { it.recipeId }.distinct().size)
        val lunchSlots = result.slots.filter { it.meal == MealType.LUNCH }
        assertEquals(7, lunchSlots.size)
        assertEquals(2, lunchSlots.groupBy { it.date.isAfter(LocalDate.of(2026, 11, 11)) }.size)
    }

    @Test
    fun aPantryItemExpiringIn2DaysRaisesTheRecipesThatUseIt() {
        val spinach = PlannerIngredient("spinach", "generic:spinach", ShopSection.VEGETABLES, 300.0)
        val rice = PlannerIngredient("rice", "generic:rice", ShopSection.GRAINS, 300.0)
        val recipes = listOf(recipe("plain", foods = listOf(rice)), recipe("green", foods = listOf(rice, spinach)))
        val me = person("me", variety = 1)
        val without = input(listOf(me), recipes)
        val with = without.copy(pantry = listOf(PantryLot("lot", "spinach", 400.0, saturday.plusDays(2), saturday)))
        val before = Planner.plan(without, seed = 1)
        val after = Planner.plan(with, seed = 1)
        val key = before.slots.first().key
        val scoreBefore = Planner.alternatives(without, before.copy(slots = before.slots.map { it.copy(recipeId = "none") }), key).first { it.recipeId == "green" }.score
        val scoreAfter = Planner.alternatives(with, after.copy(slots = after.slots.map { it.copy(recipeId = "none") }), key).first { it.recipeId == "green" }.score
        assertTrue("$scoreAfter > $scoreBefore", scoreAfter > scoreBefore)
        // Half of the recipe's grams come from the pantry: +10 of the 20 points.
        assertEquals(10.0, scoreAfter - scoreBefore, 1e-9)
        assertEquals("green", after.recipeOf(SlotKey(monday, MealType.LUNCH, "me")))
        assertTrue(after.choices.first { it.recipeId == "green" }.reasons.contains(PlanReason.UsesPantry(listOf("spinach"))))
        // An item expiring later than 4 days doesn't count.
        val later = without.copy(pantry = listOf(PantryLot("lot", "spinach", 400.0, saturday.plusDays(5), saturday)))
        assertEquals(before.total, Planner.plan(later, seed = 1).total, 1e-9)
    }

    @Test
    fun theSameSeedTwiceGivesTheSamePlanAndANewSeedChangesAnUnlockedSlot() {
        val recipes = (1..12).map { recipe("r$it", protein = 100.0 + it * 5) }
        val people = listOf(person("me"), person("kid"))
        val first = Planner.plan(input(people, recipes), seed = 42)
        assertEquals(first, Planner.plan(input(people, recipes), seed = 42))
        val previous = first.slots.associate { it.key to it.recipeId!! }
        val regenerated = Planner.plan(input(people, recipes), seed = 43, previous = previous)
        assertTrue(regenerated.slots.any { previous[it.key] != it.recipeId })
        assertEquals(first.slots.map { it.key }, regenerated.slots.map { it.key })
    }

    // --- M6.2 tests ---

    @Test
    fun aNewSeedChangesTheChoiceEvenWhenOneRecipeIsClearlyBest() {
        val recipes = listOf(recipe("best", cuisine = "czech"), recipe("other"))
        val me = person("me", variety = 1).let { it.copy(member = it.member.copy(cuisines = listOf("czech"))) }
        val first = Planner.plan(input(listOf(me), recipes, variety = 1), seed = 1)
        assertTrue(first.slots.all { it.recipeId == "best" })
        val again = Planner.plan(input(listOf(me), recipes, variety = 1), seed = 2, previous = first.slots.associate { it.key to it.recipeId!! })
        assertTrue(again.slots.any { it.recipeId == "other" })
    }

    @Test
    fun lockedSlotsNeverChange() {
        val recipes = (1..8).map { recipe("r$it") }
        val me = person("me")
        val locked = PlanSlot(
            id = "s1",
            planId = "p",
            date = monday.plusDays(1),
            meal = MealType.DINNER,
            memberId = "me",
            kind = SlotKind.BOX,
            recipeId = "r8",
            target = me.member.mealTarget(MealType.DINNER),
            locked = true,
        )
        val eatOut = PlanSlot("s2", "p", monday.plusDays(4), MealType.DINNER, "me", SlotKind.EAT_OUT)
        for (seed in 1L..20L) {
            val result = Planner.plan(input(listOf(me), recipes, kept = listOf(locked, eatOut)), seed)
            assertEquals(locked, result.slots.single { it.key == locked.key })
            assertEquals(eatOut, result.slots.single { it.key == eatOut.key })
            assertFalse(result.newKeys.contains(locked.key))
        }
    }

    @Test
    fun noPlanEverContainsAFilteredRecipe() {
        val random = Random(2026)
        val sections = ShopSection.FOOD_KINDS
        repeat(150) { round ->
            val recipes = (1..30).map { i ->
                recipe(
                    "r$i",
                    meals = MealType.entries.filter { random.nextBoolean() }.toSet(),
                    kcal = if (random.nextInt(20) == 0) 0.0 else 1500.0 + random.nextInt(1000),
                    protein = random.nextInt(20, 200).toDouble(),
                    flags = AnimalFlag.entries.filter { random.nextInt(5) == 0 }.toSet(),
                    allergens = Allergen.entries.filter { random.nextInt(8) == 0 }.toSet(),
                    foods = List(random.nextInt(1, 4)) { j -> PlannerIngredient("f$i-$j", null, sections.random(random), 200.0) },
                    prep = random.nextBoolean(),
                    freezable = random.nextBoolean(),
                    fridgeDays = random.nextInt(1, 5),
                    equipment = Equipment.entries.filter { random.nextInt(6) == 0 }.toSet(),
                )
            }
            val people = (1..random.nextInt(1, 4)).map { p ->
                person(
                    if (p == 1) "me" else "p$p",
                    diet = Diet.entries.random(random),
                    allergens = Allergen.entries.filter { random.nextInt(6) == 0 }.toSet(),
                    variety = random.nextInt(1, 4),
                    split = mapOf(MealType.BREAKFAST to 20, MealType.LUNCH to 30, MealType.DINNER to 30, MealType.SNACK to 20),
                    disliked = sections.filter { random.nextInt(8) == 0 }.map { it.name },
                    equipment = Equipment.entries.filter { random.nextBoolean() }.toSet(),
                    ratings = recipes.filter { random.nextInt(6) == 0 }.associate { it.id to if (random.nextBoolean()) 1 else -1 },
                )
            }
            val days = DayOfWeek.entries.filter { random.nextInt(3) == 0 }.ifEmpty { listOf(DayOfWeek.SUNDAY) }
            val inp = input(people, recipes, variety = random.nextInt(1, 4), cookingDays = days, today = monday.plusDays(random.nextLong(-2, 4)))
            val result = Planner.plan(inp, seed = round.toLong())
            val byId = recipes.associateBy { it.id }
            val byPerson = people.associateBy { it.id }
            for (choice in result.choices) {
                val r = byId.getValue(choice.recipeId)
                for (key in choice.slots) {
                    assertTrue("round $round: ${r.id} for $key", Planner.allowed(byPerson.getValue(key.memberId), r, key.meal, choice.kind, choice.cookDate, key.date))
                }
            }
            // Every open meal is either planned or reported as unplanned, never both.
            val open = Planner.openSlots(inp).map { it.key }.toSet()
            assertEquals(open, result.newKeys + result.unplanned)
        }
    }

    @Test
    fun threeHundredRecipesThreePeopleAWeekOfFourMealsUnder500Ms() {
        val random = Random(1)
        val recipes = (1..300).map { i ->
            recipe(
                "r$i",
                meals = when (i % 3) {
                    0 -> setOf(MealType.BREAKFAST, MealType.SNACK)
                    else -> setOf(MealType.LUNCH, MealType.DINNER)
                },
                protein = random.nextInt(50, 200).toDouble(),
                // 8 ingredients from 3 of the 8 kinds of food, so someone who dislikes fish still has recipes.
                foods = List(8) { j -> PlannerIngredient("f${(i * 7 + j) % 120}", null, ShopSection.FOOD_KINDS[(i + j % 3) % 8], 150.0) },
                costHalers = 5000.0 + random.nextInt(10000),
                minutes = random.nextInt(10, 60),
            )
        }
        val split = mapOf(MealType.BREAKFAST to 20, MealType.LUNCH to 30, MealType.DINNER to 30, MealType.SNACK to 20)
        val people = listOf(
            person("me", variety = 3, split = split, liked = listOf("MEAT", "VEGETABLES")),
            person("p2", split = split, ratings = mapOf("r10" to 1, "r11" to -1)),
            person("p3", split = split, disliked = listOf("FISH")),
        )
        val inp = input(people, recipes, variety = 3, budget = 300_000, today = monday)
        Planner.plan(inp, seed = 0) // warm-up for the JIT
        val start = System.nanoTime()
        val result = Planner.plan(inp, seed = 1)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(84, result.newKeys.size)
        assertTrue("took $ms ms", ms < 500)
    }

    @Test
    fun onePotForEveryoneWhenARecipeFitsThemAllOtherwiseOwnPots() {
        val vegan = recipe("dal")
        val meat = recipe("goulash", flags = setOf(AnimalFlag.MEAT), cuisine = "czech")
        val me = person("me", variety = 1).let { it.copy(member = it.member.copy(cuisines = listOf("czech"))) }
        val shared = Planner.plan(input(listOf(me, person("kid", diet = Diet.VEGAN, variety = 1)), listOf(vegan, meat), variety = 1), seed = 1)
        assertEquals(setOf("dal"), shared.slots.mapNotNull { it.recipeId }.toSet())
        // With nothing for both, each person gets their own pot.
        val split = Planner.plan(input(listOf(me, person("kid", diet = Diet.VEGAN, variety = 1)), listOf(meat, recipe("tofu", flags = setOf(AnimalFlag.FISH)), vegan.copy(recipe = vegan.recipe.copy(mealTypes = setOf(MealType.BREAKFAST)))), variety = 1), seed = 1)
        assertTrue(split.slots.filter { it.memberId == "me" }.all { it.recipeId == "goulash" })
        assertTrue(split.slots.none { it.memberId == "kid" })
        assertEquals(7 * 2, split.unplanned.count { it.memberId == "kid" })
    }

    @Test
    fun boxesNeedMealPrepRecipesAndFreezingForLateDays() {
        val notPrep = recipe("soup", prep = false)
        val fridgeOnly = recipe("salad", freezable = false, fridgeDays = 3)
        val me = person("me", variety = 1)
        // Wednesday's session covers until Sunday lunch: Sunday is 4 days after Wednesday.
        val wednesday = LocalDate.of(2026, 11, 11)
        val sunday = LocalDate.of(2026, 11, 15)
        assertFalse(Planner.fitsSlot(notPrep, MealType.LUNCH, SlotKind.BOX, wednesday, wednesday.plusDays(1)))
        assertTrue(Planner.fitsSlot(notPrep, MealType.LUNCH, SlotKind.COOK_FRESH, wednesday, wednesday))
        assertTrue(Planner.fitsSlot(fridgeOnly, MealType.LUNCH, SlotKind.BOX, wednesday, wednesday.plusDays(3)))
        assertFalse(Planner.fitsSlot(fridgeOnly, MealType.LUNCH, SlotKind.BOX, wednesday, sunday))
        val result = Planner.plan(input(listOf(me), listOf(notPrep, fridgeOnly), variety = 1), seed = 1)
        assertEquals(null, result.recipeOf(SlotKey(sunday, MealType.LUNCH, "me")))
        assertTrue(SlotKey(sunday, MealType.LUNCH, "me") in result.unplanned)
        assertEquals("salad", result.recipeOf(SlotKey(LocalDate.of(2026, 11, 13), MealType.LUNCH, "me")))
    }

    @Test
    fun dislikedFoodsRatedDownAndMissingEquipmentAreFilteredOut() {
        val fish = recipe("salmon", foods = listOf(PlannerIngredient("salmon", "generic:salmon", ShopSection.FISH, 400.0)))
        val oven = recipe("bake", equipment = setOf(Equipment.OVEN))
        val down = recipe("meh")
        val ok = recipe("ok")
        val me = person("me", disliked = listOf("FISH"), equipment = setOf(Equipment.MICROWAVE), ratings = mapOf("meh" to -1))
        assertFalse(Planner.allowedForPerson(me, fish))
        assertFalse(Planner.allowedForPerson(me, oven))
        assertFalse(Planner.allowedForPerson(me, down))
        assertTrue(Planner.allowedForPerson(me, ok))
        // No equipment set means: don't filter by equipment.
        assertTrue(Planner.allowedForPerson(person("me"), oven))
        // Disliked by food key too.
        assertFalse(Planner.allowedForPerson(person("me", disliked = listOf("generic:salmon")), fish))
        // A recipe without calories can't be sized.
        assertFalse(Planner.allowedForPerson(person("me"), recipe("water", kcal = 0.0)))
    }

    @Test
    fun scoresFollowTheA15Table() {
        val chicken = PlannerIngredient("chicken", "generic:chicken_breast_raw", ShopSection.MEAT, 300.0)
        val pepper = PlannerIngredient("pepper", "generic:pepper", ShopSection.VEGETABLES, 300.0)
        val liked = recipe("liked", foods = listOf(chicken, pepper), cuisine = "czech")
        val plain = recipe("plain", foods = listOf(PlannerIngredient("x", null, ShopSection.OTHER, 600.0)))
        val me = person("me", variety = 1, liked = listOf("MEAT", "VEGETABLES", "FRUIT"), ratings = mapOf("liked" to 1))
            .let { it.copy(member = it.member.copy(cuisines = listOf("czech"))) }
        val inp = input(listOf(me), listOf(liked, plain), variety = 1)
        val result = Planner.plan(inp, seed = 1)
        val key = SlotKey(monday, MealType.LUNCH, "me")
        val alts = Planner.alternatives(inp, result.copy(slots = result.slots.map { it.copy(recipeId = "none") }), key).associateBy { it.recipeId }
        // +20 rated, +20 liked (two liked foods), +10 cuisine; protein share 0.30 = target, so both get +15.
        assertEquals(50.0, alts.getValue("liked").score - alts.getValue("plain").score, 1e-9)
        assertEquals(15.0, alts.getValue("plain").score, 1e-9)
        assertEquals(
            listOf(PlanReason.RatedUp, PlanReason.LikedFoods(listOf("MEAT", "VEGETABLES")), PlanReason.GoodProtein),
            alts.getValue("liked").reasons,
        )
    }

    @Test
    fun usedLastWeekCostsMoreWithMoreVariety() {
        val recipes = listOf(recipe("a", protein = 150.0), recipe("b", protein = 140.0))
        for (variety in 1..3) {
            val me = person("me", variety = variety)
            val inp = input(listOf(me), recipes, variety = 1).copy(recent = mapOf("me" to setOf("a")))
            val result = Planner.plan(inp, seed = 1)
            val key = SlotKey(monday, MealType.LUNCH, "me")
            val score = Planner.alternatives(inp, result.copy(slots = result.slots.map { it.copy(recipeId = "none") }), key).first { it.recipeId == "a" }.score
            assertEquals(15.0 - 15.0 * (variety - 1), score, 1e-9)
        }
    }

    @Test
    fun cheapMealsScoreHigherWithABudget() {
        val cheap = recipe("cheap", costHalers = 2000.0)
        val dear = recipe("dear", costHalers = 20000.0)
        val me = person("me", variety = 1)
        // 14 meals, 1,400 Kč a week: 100 Kč a meal.
        val inp = input(listOf(me), listOf(cheap, dear), variety = 1, budget = 140_000)
        val result = Planner.plan(inp, seed = 1)
        assertTrue(result.slots.all { it.recipeId == "cheap" })
        val key = SlotKey(monday, MealType.LUNCH, "me")
        val alts = Planner.alternatives(inp, result.copy(slots = result.slots.map { it.copy(recipeId = "none") }), key).associateBy { it.recipeId }
        // A meal is 1,000 kcal, half the pot. cheap: 10 Kč, 10 % of the budget → all 15 points; dear: 100 Kč, 100 % → half.
        assertEquals(15.0 + 15.0, alts.getValue("cheap").score, 1e-9)
        assertEquals(15.0 + 7.5, alts.getValue("dear").score, 1e-9)
        assertTrue(PlanReason.Cheap in alts.getValue("cheap").reasons)
    }

    @Test
    fun aSessionOverTheMinutesIsAvoidedWhenPossible() {
        val long = recipe("long", minutes = 100, protein = 150.0)
        val longer = recipe("longer", minutes = 100, protein = 150.0)
        val short = recipe("short", minutes = 20, protein = 120.0)
        val me = person("me", variety = 1)
        // Lunch and dinner from one session: two long recipes would be 200 minutes.
        val result = Planner.plan(input(listOf(me), listOf(long, longer, short), variety = 1, minutes = 120), seed = 5)
        for (day in result.choices.filter { it.kind == SlotKind.BOX }.groupBy { it.cookDate }.values) {
            val minutes = day.map { it.recipeId }.distinct().sumOf { id -> listOf(long, longer, short).first { it.id == id }.activeMinutes }
            assertTrue("$minutes minutes", minutes <= 120)
        }
    }

    @Test
    fun boxesWhoseSessionIsOverAreCookedFresh() {
        val me = person("me", variety = 1)
        val thursday = LocalDate.of(2026, 11, 12)
        val result = Planner.plan(input(listOf(me), listOf(recipe("a"), recipe("b", prep = false)), variety = 1, today = thursday), seed = 1)
        // Thursday to Sunday lunch would come from Wednesday's session, which is over: cooked fresh.
        val sunday = LocalDate.of(2026, 11, 15)
        for (slot in result.slots.filter { it.date.isBefore(sunday) || it.meal == MealType.LUNCH }) assertEquals(slot.toString(), SlotKind.COOK_FRESH, slot.kind)
        assertTrue(result.slots.none { it.date.isBefore(thursday) })
        // Sunday dinner comes from Sunday's session.
        assertEquals(SlotKind.BOX, result.slots.single { it.date == sunday && it.meal == MealType.DINNER }.kind)
    }

    @Test
    fun chunksSplitDaysIntoRuns() {
        val days = (0L..4L).map { monday.plusDays(it) }
        assertEquals(listOf(days), Planner.chunks(days, 1))
        assertEquals(listOf(days.take(3), days.drop(3)), Planner.chunks(days, 2))
        assertEquals(days.map { listOf(it) }, Planner.chunks(days, 5))
        assertEquals(days.map { listOf(it) }, Planner.chunks(days, 9))
    }

    @Test
    fun withVariety3EveryDayOfASessionGetsADifferentRecipe() {
        val recipes = (1..10).map { recipe("r$it") }
        val result = Planner.plan(input(listOf(person("me", variety = 3)), recipes, variety = 3), seed = 9)
        for (session in result.choices.filter { it.kind == SlotKind.BOX }.groupBy { it.cookDate to it.slots.first().meal }.values) {
            assertEquals(session.size, session.map { it.recipeId }.distinct().size)
            assertTrue(session.all { c -> c.slots.map { it.date }.distinct().size == 1 })
        }
        assertNotEquals(1, result.slots.mapNotNull { it.recipeId }.distinct().size)
    }
}
