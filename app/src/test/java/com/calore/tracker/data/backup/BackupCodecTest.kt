package com.calore.tracker.data.backup

import com.calore.tracker.domain.newId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class BackupCodecTest {

    private val food = FoodRow(
        id = 1,
        uuid = "0f8fad5b-d9cb-469f-a165-70867728950e",
        name = "Kuřecí prsa \"chlazená\"",
        brand = "Vodňanské",
        barcode = "8590000000001",
        kcalPer100g = 110.0,
        proteinPer100g = 23.0,
        carbsPer100g = 0.0,
        fatPer100g = 1.5,
        fiberPer100g = null,
        sugarPer100g = null,
        servingGrams = 125.0,
        source = "OPEN_FOOD_FACTS",
        lastUsedAt = 1_762_000_000_000,
        updatedAt = 1_761_000_000_000,
    )

    private fun entry(id: Long, uuid: String = newId(), date: String = "2026-11-06") = DiaryEntryRow(
        id = id,
        uuid = uuid,
        date = date,
        meal = "LUNCH",
        foodId = 1,
        name = "Kuřecí prsa",
        grams = 150.0,
        kcal = 165.0,
        protein = 34.5,
        carbs = 0.0,
        fat = 2.25,
        fiber = null,
        sugar = null,
        createdAt = 1_762_400_000_000 + id,
    )

    /** A dish logged by servings (M3.5). */
    private val dish = entry(3).copy(
        foodId = null,
        name = "Kuře na paprice",
        grams = 0.0,
        entryType = "DISH",
        recipeId = "3b241101-e2bb-4255-8caf-4136c566a962",
        servings = 1.5,
    )

    private val weight = WeightEntryRow(id = 1, uuid = "7c9e6679-7425-40de-944b-e07fc1f90ae7", date = "2026-11-06", kg = 80.4)

    private val me = MemberRow(
        id = "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
        kind = "ME",
        name = "",
        colour = -13730510,
        sex = "FEMALE",
        birthDate = "1990-07-01",
        heightCm = 168.0,
        activity = "LIGHT",
        goalType = "CUT",
        goalRatePct = 0.5,
        macroMode = "AUTO",
        targetKcal = 1800,
        targetProteinG = 130,
        targetCarbsG = 190,
        targetFatG = 50,
        mealSplit = mapOf("BREAKFAST" to 20, "LUNCH" to 30, "DINNER" to 30, "SNACK" to 20),
        diet = "OMNIVORE",
        allergens = listOf("PEANUTS"),
        likedFoods = emptyList(),
        dislikedFoods = emptyList(),
        cuisines = listOf("czech"),
        equipment = emptyList(),
        variety = 2,
        cookSessionsPerWeek = 2,
        cookDays = listOf("SUNDAY", "WEDNESDAY"),
        minutesPerSession = 120,
        preppedMeals = listOf("LUNCH", "DINNER"),
        sharesTargets = false,
        createdAt = 1,
        updatedAt = 2,
    )

    private val measurement = BodyMeasurementRow(
        id = "9b2c6a1e-1d2f-4c3b-8a4e-5f6a7b8c9d0e",
        memberId = me.id,
        date = "2026-11-01",
        neckCm = 32.0,
        waistCm = 70.0,
        hipCm = 95.0,
        bodyFatPct = 24.9,
        method = "NAVY",
        createdAt = 3,
        updatedAt = 3,
    )

    private val checkin = CheckinRow(
        id = "1c9a8b7d-6e5f-4a3b-9c2d-1e0f9a8b7c6d",
        memberId = me.id,
        weekStart = "2026-11-02",
        trendStartKg = 80.0,
        trendEndKg = 79.1,
        avgIntakeKcal = 2500.0,
        countedDays = 21,
        estimatedMaintenanceKcal = 2773,
        oldTargetKcal = 2200,
        proposedTargetKcal = 2340,
        accepted = true,
        createdAt = 4,
    )

    private val recipe = RecipeRow(
        id = "3b241101-e2bb-4255-8caf-4136c566a962",
        name = "Kuře na paprice",
        nameCs = "Kuře na paprice",
        nameEn = "Chicken paprikash",
        shortCode = "K",
        servings = 4,
        mealTypes = listOf("LUNCH", "DINNER"),
        tags = listOf("czech"),
        equipment = listOf("OVEN"),
        freezable = true,
        learnedYield = 0.85,
        learnedYieldCount = 1,
        createdAt = 5,
        updatedAt = 6,
    )

    private val ingredient = RecipeIngredientRow(
        id = "a3bb189e-8bf9-4888-9912-ace4e6543002",
        recipeId = recipe.id,
        position = 0,
        foodUuid = food.uuid,
        grams = 600.0,
    )

    private val step = RecipeStepRow(
        id = "c56a4180-65aa-42ec-a945-5fd21dec0538",
        recipeId = recipe.id,
        position = 0,
        text = "Osmahněte cibuli.",
        textEn = "Fry the onion.",
        minutes = 5,
        equipment = "STOVE",
    )

    private val pot = PotRow(id = "6ba7b810-9dad-41d1-80b4-00c04fd430c8", name = "Velký hrnec", emptyGrams = 1850.0, createdAt = 7, updatedAt = 7)

    private val batch = BatchRow(
        id = "6ba7b811-9dad-41d1-80b4-00c04fd430c8",
        recipeId = recipe.id,
        name = "Chili",
        cookedAt = 8,
        cookedByMemberId = null,
        rawGrams = 3200.0,
        cookedGrams = 3000.0,
        kcalPer100g = 120.0,
        proteinPer100g = 9.0,
        createdAt = 8,
        updatedAt = 8,
    )

    private val box = BoxRow(
        id = "6ba7b812-9dad-41d1-80b4-00c04fd430c8",
        code = "C1",
        ownerMemberId = me.id,
        storage = "FRIDGE",
        state = "READY",
        eatByDate = "2026-11-11",
        plannedDate = "2026-11-09",
        plannedMeal = "LUNCH",
        createdAt = 9,
        updatedAt = 9,
    )

    private fun backup(
        foods: List<FoodRow> = listOf(food),
        diary: List<DiaryEntryRow> = listOf(entry(1), entry(2), dish),
        weights: List<WeightEntryRow> = listOf(weight),
        version: Int = BackupCodec.VERSION,
        members: List<MemberRow> = listOf(me),
        measurements: List<BodyMeasurementRow> = listOf(measurement),
        checkins: List<CheckinRow> = listOf(checkin),
        recipes: List<RecipeRow> = listOf(recipe),
        ingredients: List<RecipeIngredientRow> = listOf(ingredient),
        steps: List<RecipeStepRow> = listOf(step),
        boxes: List<BoxRow> = listOf(box),
        ratings: List<RecipeRatingRow> = listOf(RecipeRatingRow("6ba7b815-9dad-41d1-80b4-00c04fd430c8", me.id, recipe.id, 1, 10, 10)),
        cyclePeriods: List<CyclePeriodRow> = listOf(CyclePeriodRow("6ba7b816-9dad-41d1-80b4-00c04fd430c8", "2026-10-01", "2026-10-05", 11, 11)),
    ) = BackupFile(
        format = BackupCodec.FORMAT,
        version = version,
        exportedAt = "2026-11-06T18:00:00Z",
        appVersion = "0.2.0",
        tables = BackupTables(
            foods, diary, weights, members, measurements, checkins, recipes, ingredients, steps,
            pots = listOf(pot),
            batches = listOf(batch),
            batchIngredients = listOf(BatchIngredientRow("6ba7b813-9dad-41d1-80b4-00c04fd430c8", batch.id, food.uuid, 800.0)),
            boxes = boxes,
            boxParts = listOf(BoxPartRow("6ba7b814-9dad-41d1-80b4-00c04fd430c8", box.id, batch.id, 500.0)),
            recipeRatings = ratings,
            cyclePeriods = cyclePeriods,
            cycleDays = listOf(CycleDayRow("6ba7b817-9dad-41d1-80b4-00c04fd430c8", "2026-10-02", "MEDIUM", listOf("CRAMPS"), "note", 12, 12)),
        ),
    )

    private fun decode(backup: BackupFile) = BackupCodec.decode(BackupCodec.encode(backup))

    @Test
    fun roundTripKeepsEveryValue() {
        val original = backup()
        assertEquals(BackupReadResult.Ok(original), decode(original))
    }

    @Test
    fun fileHasTheDocumentedShape() {
        val root = Json.parseToJsonElement(BackupCodec.encode(backup())).jsonObject
        assertEquals(setOf("format", "version", "exportedAt", "appVersion", "tables"), root.keys)
        assertEquals("\"calore-backup\"", root["format"].toString())
        assertEquals(BackupCodec.VERSION.toString(), root["version"].toString())
        assertEquals(
            setOf(
                "foods", "diary_entries", "weight_entries", "members", "body_measurements", "checkins",
                "recipes", "recipe_ingredients", "recipe_steps",
                "pots", "batches", "batch_ingredients", "boxes", "box_parts",
                "plans", "plan_slots", "plan_cooks", "pantry_items", "stores", "store_products", "prices", "shopping_items",
                "recipe_ratings", "cycle_periods", "cycle_days",
            ),
            root["tables"]!!.jsonObject.keys,
        )
    }

    @Test
    fun version1FilesStillImportAndLeaveProfileTablesAlone() {
        val v1 = """{"format":"calore-backup","version":1,"exportedAt":"2026-11-06T18:00:00Z","appVersion":"0.2.0",
            "tables":{"foods":[],"diary_entries":[],"weight_entries":[]}}"""
        val tables = (BackupCodec.decode(v1) as BackupReadResult.Ok).backup.tables
        assertNull("null means: keep the members on the phone", tables.members)
        assertNull(tables.bodyMeasurements)
        assertNull(tables.checkins)
        assertNull("null means: keep the recipes on the phone", tables.recipes)
        assertNull(tables.recipeIngredients)
        assertNull(tables.recipeSteps)
    }

    @Test
    fun brokenRecipeRowsAreDamaged() {
        val cases = mapOf(
            "bad recipe id" to backup(recipes = listOf(recipe.copy(id = "r1"))),
            "zero servings" to backup(recipes = listOf(recipe.copy(servings = 0))),
            "blank box code" to backup(recipes = listOf(recipe.copy(shortCode = " "))),
            "two recipes with one key" to backup(
                recipes = listOf(recipe.copy(key = "starter:cz:a"), recipe.copy(id = newId(), key = "starter:cz:a")),
            ),
            "negative grams" to backup(ingredients = listOf(ingredient.copy(grams = -5.0))),
            "duplicate step" to backup(steps = listOf(step, step)),
            "negative servings" to backup(diary = listOf(dish.copy(servings = -1.0))),
            "box without eat-by date" to backup(boxes = listOf(box.copy(eatByDate = "soon"))),
            "box more than full" to backup(boxes = listOf(box.copy(remainingFraction = 1.5))),
        )
        for ((name, broken) in cases) {
            assertEquals(name, BackupReadResult.Damaged, decode(broken))
        }
    }

    @Test
    fun ratingsOtherThanUpOrDownAreDamaged() {
        val rating = RecipeRatingRow("6ba7b815-9dad-41d1-80b4-00c04fd430c8", me.id, recipe.id, 1, 10, 10)
        assertEquals(BackupReadResult.Damaged, decode(backup(ratings = listOf(rating.copy(rating = 2)))))
        assertEquals(BackupReadResult.Damaged, decode(backup(ratings = listOf(rating.copy(id = "x")))))
        assertEquals(BackupReadResult.Damaged, decode(backup(ratings = listOf(rating, rating))))
    }

    @Test
    fun brokenCycleRowsAreDamaged() {
        val period = CyclePeriodRow("6ba7b816-9dad-41d1-80b4-00c04fd430c8", "2026-10-01", null, 11, 11)
        assertEquals(BackupReadResult.Damaged, decode(backup(cyclePeriods = listOf(period.copy(startDate = "October")))))
        assertEquals(BackupReadResult.Damaged, decode(backup(cyclePeriods = listOf(period, period.copy(id = "6ba7b818-9dad-41d1-80b4-00c04fd430c8")))))
    }

    @Test
    fun brokenProfileRowsAreDamaged() {
        val cases = mapOf(
            "bad member id" to backup(members = listOf(me.copy(id = "me"))),
            "bad birth date" to backup(members = listOf(me.copy(birthDate = "1990-02-30"))),
            "negative height" to backup(members = listOf(me.copy(heightCm = -1.0))),
            "two check-ins in one week" to backup(checkins = listOf(checkin, checkin.copy(id = newId()))),
            "measurement without date" to backup(measurements = listOf(measurement.copy(date = ""))),
            "diary for unknown member id format" to backup(diary = listOf(entry(1).copy(memberId = "child"))),
        )
        for ((name, broken) in cases) {
            assertEquals(name, BackupReadResult.Damaged, decode(broken))
        }
    }

    @Test
    fun emptyDatabaseRoundTrips() {
        val empty = backup(
            foods = emptyList(),
            diary = emptyList(),
            weights = emptyList(),
            members = emptyList(),
            measurements = emptyList(),
            checkins = emptyList(),
        )
        val result = decode(empty) as BackupReadResult.Ok
        assertEquals(BackupCounts(0, 0, 0), result.backup.tables.counts)
    }

    @Test
    fun tablesMissingFromAnOlderFileAreEmptyAndUnknownKeysAreIgnored() {
        val text = """{"format":"calore-backup","version":1,"exportedAt":"2026-11-06T18:00:00Z",
            "appVersion":"0.2.0","comment":"hi","tables":{"weight_entries":[
            {"id":1,"uuid":"7c9e6679-7425-40de-944b-e07fc1f90ae7","date":"2026-11-06","kg":80.4,"note":"x"}]}}"""
        val result = BackupCodec.decode(text) as BackupReadResult.Ok
        assertEquals(BackupCounts(foods = 0, diaryEntries = 0, weightEntries = 1), result.backup.tables.counts)
    }

    @Test
    fun foreignFilesAreNotBackups() {
        assertEquals(BackupReadResult.NotABackup, BackupCodec.decode(""))
        assertEquals(BackupReadResult.NotABackup, BackupCodec.decode("hello"))
        assertEquals(BackupReadResult.NotABackup, BackupCodec.decode("\u0089PNG\r\n\u001a\n"))
        assertEquals(BackupReadResult.NotABackup, BackupCodec.decode("[1,2,3]"))
        assertEquals(BackupReadResult.NotABackup, BackupCodec.decode("""{"name":"some other app"}"""))
        assertEquals(BackupReadResult.NotABackup, BackupCodec.decode("""{"format":"other-backup","version":1}"""))
    }

    @Test
    fun newerVersionsAreRefused() {
        val newer = BackupCodec.VERSION + 1
        assertEquals(BackupReadResult.NewerVersion(newer), decode(backup(version = newer)))
    }

    @Test
    fun brokenBackupsAreDamaged() {
        val text = BackupCodec.encode(backup())
        assertEquals("cut off", BackupReadResult.Damaged, BackupCodec.decode(text.substring(0, text.length / 2)))
        assertEquals(
            "no version",
            BackupReadResult.Damaged,
            BackupCodec.decode("""{"format":"calore-backup","tables":{}}"""),
        )
        assertEquals("version 0", BackupReadResult.Damaged, decode(backup(version = 0)))
        assertEquals(
            "missing header field",
            BackupReadResult.Damaged,
            BackupCodec.decode("""{"format":"calore-backup","version":1,"tables":{}}"""),
        )
        assertEquals(
            "wrong type",
            BackupReadResult.Damaged,
            BackupCodec.decode(text.replace("\"kg\":80.4", "\"kg\":\"heavy\"")),
        )
        assertEquals(
            "missing row field",
            BackupReadResult.Damaged,
            BackupCodec.decode(text.replace("\"kg\":80.4", "\"weight\":80.4")),
        )
    }

    @Test
    fun rowsTheDatabaseWouldRefuseAreDamaged() {
        val sameUuid = newId()
        val cases = mapOf(
            "duplicate diary uuid" to backup(diary = listOf(entry(1, sameUuid), entry(2, sameUuid))),
            "duplicate diary id" to backup(diary = listOf(entry(1), entry(1))),
            "duplicate barcode" to backup(foods = listOf(food, food.copy(id = 2, uuid = newId()))),
            "duplicate food uuid" to backup(foods = listOf(food, food.copy(id = 2, barcode = null))),
            "two weights on one day" to backup(weights = listOf(weight, weight.copy(id = 2, uuid = newId()))),
            "bad uuid" to backup(diary = listOf(entry(1, uuid = "not-a-uuid"))),
            "empty uuid" to backup(weights = listOf(weight.copy(uuid = ""))),
            "bad date" to backup(diary = listOf(entry(1, date = "2026-13-40"))),
            "negative kcal" to backup(foods = listOf(food.copy(kcalPer100g = -1.0))),
            "negative nutrient" to backup(diary = listOf(entry(1).copy(protein = -3.0))),
            "zero weight" to backup(weights = listOf(weight.copy(kg = 0.0))),
            "zero id" to backup(foods = listOf(food.copy(id = 0))),
        )
        for ((name, broken) in cases) {
            assertEquals(name, BackupReadResult.Damaged, decode(broken))
        }
    }

    @Test
    fun tenThousandDiaryEntriesRoundTrip() {
        val diary = (1L..10_000L).map { entry(it, date = "2026-${"%02d".format(1 + it % 12)}-01") }
        val original = backup(diary = diary)
        val text = BackupCodec.encode(original)
        assertTrue("about 300 bytes per entry", text.length < 5_000_000)
        assertEquals(BackupReadResult.Ok(original), BackupCodec.decode(text))
    }

    @Test
    fun readAtMostStopsAtTheLimit() {
        val bytes = ByteArray(1_000) { 'a'.code.toByte() }
        assertEquals(1_000, ByteArrayInputStream(bytes).readAtMost(1_000)!!.size)
        assertNull(ByteArrayInputStream(bytes).readAtMost(999))
        assertEquals(0, ByteArrayInputStream(ByteArray(0)).readAtMost(10)!!.size)
    }
}
