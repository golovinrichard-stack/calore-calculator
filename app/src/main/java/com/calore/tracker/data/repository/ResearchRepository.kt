package com.calore.tracker.data.repository

import com.calore.tracker.data.db.CookingDao
import com.calore.tracker.data.db.DiaryDao
import com.calore.tracker.data.db.DiaryEntryEntity
import com.calore.tracker.data.db.FoodDao
import com.calore.tracker.data.db.JsonColumns
import com.calore.tracker.data.db.ResearchDao
import com.calore.tracker.data.db.ResearchReportEntity
import com.calore.tracker.data.db.toDomain
import com.calore.tracker.data.prefs.ResearchConsent
import com.calore.tracker.data.prefs.ResearchPrefs
import com.calore.tracker.data.remote.ResearchClient
import com.calore.tracker.domain.BoxState
import com.calore.tracker.domain.EntryType
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.ReminderSchedule
import com.calore.tracker.domain.ShopSection
import com.calore.tracker.domain.SlotKind
import com.calore.tracker.domain.newId
import com.calore.tracker.domain.parseIsoDate
import com.calore.tracker.domain.research.RESEARCH_CONSENT_VERSION
import com.calore.tracker.domain.research.ResearchCategory
import com.calore.tracker.domain.research.ResearchEntry
import com.calore.tracker.domain.research.ResearchProfile
import com.calore.tracker.domain.research.ResearchReports
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.Base64

/** A stored report, for "See what we send" (M10.4). */
data class ResearchReportView(val id: String, val isoWeek: String, val status: String, val attempts: Int, val payload: String, val sentAt: Long?)

/** What happened when sending or deleting. */
enum class ResearchOutcome { DONE, WAITING, NOTHING_TO_DO }

/**
 * The research program (M10): consent, the weekly report (A21), sending, stopping and deleting.
 * Nothing is sent unless the user agreed, and only the categories they chose (policy appendix A).
 */
class ResearchRepository(
    private val prefs: ResearchPrefs,
    private val researchDao: ResearchDao,
    private val client: ResearchClient,
    private val diaryDao: DiaryDao,
    private val foodDao: FoodDao,
    private val cookingDao: CookingDao,
    private val members: MemberRepository,
    private val weights: WeightRepository,
    private val body: BodyRepository,
    private val plans: PlanRepository,
    private val recipes: RecipeRepository,
    private val cycle: CycleRepository,
    private val supplements: SupplementRepository,
    private val appVersion: String,
    private val clock: Clock,
    private val random: SecureRandom = SecureRandom(),
) {
    val consent: Flow<ResearchConsent> = prefs.consent

    fun reports(): Flow<List<ResearchReportView>> = researchDao.observeReports().map { rows ->
        rows.map { ResearchReportView(it.id, it.isoWeek, it.status, it.attempts, it.payload, it.sentAt) }
    }

    /**
     * "I agree to send the selected data" (M10.2 step 6): a new participant id and secret, the
     * consent stored, and the registration sent (or sent later when offline).
     */
    suspend fun agree(categories: Set<ResearchCategory>, region: String?): ResearchOutcome {
        require(categories.isNotEmpty()) { "Agreeing needs at least one category" }
        val secret = ByteArray(SECRET_BYTES).also(random::nextBytes)
        val consent = ResearchConsent(
            participantId = newId(),
            participantSecret = Base64.getEncoder().encodeToString(secret),
            consentVersion = RESEARCH_CONSENT_VERSION,
            consentedAt = clock.millis(),
            categories = categories,
            region = region?.takeIf { it in ResearchReports.REGIONS },
            registrationPending = true,
        )
        prefs.save(consent)
        return register()
    }

    /** Sends the registration if it hasn't reached the server yet. */
    suspend fun register(): ResearchOutcome {
        val consent = prefs.consent.first()
        if (!consent.registrationPending || !consent.active) return ResearchOutcome.NOTHING_TO_DO
        val body = registrationJson(consent) ?: return ResearchOutcome.NOTHING_TO_DO
        return when (client.register(body)) {
            ResearchClient.Result.Ok -> {
                prefs.save(consent.copy(registrationPending = false))
                ResearchOutcome.DONE
            }
            else -> ResearchOutcome.WAITING
        }
    }

    /** The registration JSON (appendix A) plus the secret, which the server hashes and never stores. */
    private suspend fun registrationJson(consent: ResearchConsent): String? {
        val id = consent.participantId ?: return null
        val me = members.ensureMe()
        val profile = ResearchProfile(me.birthDate, me.sex, consent.region, me.diet, me.goalType, me.activity)
        val json = ResearchReports.registration(id, consent.categories, profile, LocalDate.now(clock))
        return JsonObject(json + ("secret" to JsonPrimitive(consent.participantSecret))).toString()
    }

    /**
     * A21: the report for the week that starts on [weekStart], stored as PENDING with its exact JSON.
     * Nothing when not taking part, or when the week has its report already.
     */
    suspend fun buildReport(weekStart: LocalDate): ResearchReportView? {
        val consent = prefs.consent.first()
        if (!consent.active) return null
        val monday = ReminderSchedule.weekStart(weekStart)
        val isoWeek = ResearchReports.isoWeek(monday)
        researchDao.reportFor(isoWeek)?.let { return null }
        val week = monday..monday.plusDays(6)
        val agreed = consent.categories
        val me = members.ensureMe()
        val target = me.effectiveTargets.kcal
        val sections = buildMap<ResearchCategory, JsonObject?> {
            if (ResearchCategory.NUTRITION in agreed) put(ResearchCategory.NUTRITION, nutrition(week, me.id, target))
            if (ResearchCategory.BODY in agreed) {
                val bodyFat = body.measurements().first().mapNotNull { m -> m.bodyFatPct?.let { m.date to it } }
                put(ResearchCategory.BODY, ResearchReports.body(week, me.heightCm, weights.entries().first(), bodyFat))
            }
            if (ResearchCategory.CYCLE in agreed) {
                val settings = cycle.settings.first()
                if (settings.enabled) {
                    val daily = diaryDao.dailyKcal(week.start.minusDays(CYCLE_LOOKBACK_DAYS).toString(), week.endInclusive.toString())
                        .mapNotNull { row -> parseIsoDate(row.date)?.let { it to row.kcal } }.toMap()
                    put(
                        ResearchCategory.CYCLE,
                        ResearchReports.cycle(week, cycle.periodsNow(), cycle.days().first(), settings.contraception, settings.defaultPeriodLength, daily, target),
                    )
                }
            }
            if (ResearchCategory.SUPPLEMENTS in agreed) {
                put(
                    ResearchCategory.SUPPLEMENTS,
                    ResearchReports.supplements(week, supplements.supplementsNow(), supplements.logsSince(week.start).first(), clock.zone),
                )
            }
        }
        val id = newId()
        val json = ResearchReports.report(id, consent.participantId!!, monday, appVersion, sections, agreed).toString()
        val row = ResearchReportEntity(id, isoWeek, agreed.map { it.name }, json, STATUS_PENDING, 0, clock.millis(), null)
        researchDao.upsert(row)
        prefs.save(consent.copy(lastReportWeek = isoWeek))
        return ResearchReportView(row.id, row.isoWeek, row.status, 0, row.payload, null)
    }

    /** Sends every PENDING report; after [MAX_ATTEMPTS] failures a report is FAILED until next week. */
    suspend fun sendPending(): ResearchOutcome {
        val consent = prefs.consent.first()
        if (!consent.active) return ResearchOutcome.NOTHING_TO_DO
        if (register() == ResearchOutcome.WAITING) return ResearchOutcome.WAITING
        val secret = consent.participantSecret ?: return ResearchOutcome.NOTHING_TO_DO
        var waiting = false
        for (report in researchDao.pending()) {
            when (client.sendReport(secret, report.payload)) {
                ResearchClient.Result.Ok -> researchDao.upsert(report.copy(status = STATUS_SENT, sentAt = clock.millis()))
                else -> {
                    val attempts = report.attempts + 1
                    val status = if (attempts >= MAX_ATTEMPTS) STATUS_FAILED else STATUS_PENDING
                    researchDao.upsert(report.copy(attempts = attempts, status = status))
                    if (status == STATUS_PENDING) waiting = true
                }
            }
        }
        return if (waiting) ResearchOutcome.WAITING else ResearchOutcome.DONE
    }

    /** "The next week tries again" (M10.4): FAILED reports become PENDING with fresh attempts. */
    suspend fun retryFailed() {
        researchDao.observeReports().first().filter { it.status == STATUS_FAILED }.forEach { researchDao.upsert(it.copy(status = STATUS_PENDING, attempts = 0)) }
    }

    /** "Stop sharing" (M10.5): reports not sent yet are deleted, and the date is kept. */
    suspend fun stop() {
        val consent = prefs.consent.first()
        researchDao.deleteUnsent()
        prefs.save(consent.copy(withdrawnAt = clock.millis(), registrationPending = false))
    }

    /**
     * "Delete my data from the server" (M10.5). Offline, it waits ("Waiting to delete") and is tried
     * again; once the server confirms, the participant id, the secret and the reports go from the phone.
     */
    suspend fun deleteFromServer(): ResearchOutcome {
        val consent = prefs.consent.first()
        if (consent.participantId == null) return ResearchOutcome.NOTHING_TO_DO
        researchDao.deleteUnsent()
        prefs.save(consent.copy(pendingDeletion = true))
        return processPendingDeletion()
    }

    suspend fun processPendingDeletion(): ResearchOutcome {
        val consent = prefs.consent.first()
        if (!consent.pendingDeletion) return ResearchOutcome.NOTHING_TO_DO
        val id = consent.participantId
        val secret = consent.participantSecret
        // Never registered (registration still pending): there is nothing on the server.
        val gone = id == null || secret == null || consent.registrationPending || when (val result = client.deleteParticipant(id, secret)) {
            ResearchClient.Result.Ok -> true
            is ResearchClient.Result.Refused -> result.code == HTTP_NOT_FOUND
            is ResearchClient.Result.Failed -> false
        }
        if (!gone) return ResearchOutcome.WAITING
        researchDao.deleteAll()
        prefs.save(ResearchConsent(withdrawnAt = consent.withdrawnAt ?: clock.millis()))
        return ResearchOutcome.DONE
    }

    // --- Nutrition data for A21 ---

    private suspend fun nutrition(week: ClosedRange<LocalDate>, meId: String, target: Int): JsonObject? {
        val rows = diaryDao.entriesBetween(week.start.toString(), week.endInclusive.toString())
        val sectionsCache = HashMap<String, Map<ShopSection, Double>>()
        val entries = rows.mapNotNull { row -> researchEntry(row, sectionsCache) }
        val slots = plans.slotsBetween(week.start, week.endInclusive).first()
            .filter { it.memberId == meId && (it.kind == SlotKind.BOX || it.kind == SlotKind.COOK_FRESH) }
        val wasted = cookingDao.observeWastedBoxes().first().count { box ->
            (box.ownerMemberId == null || box.ownerMemberId == meId) && box.state == BoxState.WASTED.name &&
                Instant.ofEpochMilli(box.updatedAt).atZone(clock.zone).toLocalDate() in week
        }
        return ResearchReports.nutrition(week, entries, target, slots.size, slots.count { it.done }, wasted)
    }

    private suspend fun researchEntry(row: DiaryEntryEntity, cache: HashMap<String, Map<ShopSection, Double>>): ResearchEntry? {
        val date = parseIsoDate(row.date) ?: return null
        val type = JsonColumns.enumByName<EntryType>(row.entryType) ?: EntryType.FOOD
        val sections: Map<ShopSection, Double> = when (type) {
            EntryType.FOOD -> row.foodId?.let { foodDao.getById(it)?.toDomain()?.section }?.let { mapOf(it to 1.0) }.orEmpty()
            EntryType.DISH -> row.recipeId?.let { recipeSections(it, cache) }.orEmpty()
            EntryType.BOX -> row.boxId?.let { boxId ->
                cookingDao.parts(boxId).firstNotNullOfOrNull { part -> cookingDao.batch(part.batchId)?.recipeId }
            }?.let { recipeSections(it, cache) }.orEmpty()
            EntryType.SUPPLEMENT -> mapOf(ShopSection.SUPPLEMENTS to 1.0)
            EntryType.QUICK_ADD -> emptyMap()
        }
        return ResearchEntry(
            date = date,
            meal = JsonColumns.enumByName<MealType>(row.meal) ?: MealType.SNACK,
            kcal = row.kcal,
            protein = row.protein,
            carbs = row.carbs,
            fat = row.fat,
            fiber = row.fiber,
            sugar = row.sugar,
            type = type,
            sections = sections,
        )
    }

    /** A recipe's calories by food group, as fractions. */
    private suspend fun recipeSections(recipeId: String, cache: HashMap<String, Map<ShopSection, Double>>): Map<ShopSection, Double> {
        cache[recipeId]?.let { return it }
        val details = recipes.details(recipeId) ?: return emptyMap()
        val kcal = HashMap<ShopSection, Double>()
        for (i in details.ingredients.filter { !it.optional }) {
            val food = details.foods[i.foodUuid] ?: continue
            kcal[food.section] = (kcal[food.section] ?: 0.0) + food.per100g.kcal * i.grams / 100
        }
        val total = kcal.values.sum()
        val shares = if (total > 0) kcal.mapValues { it.value / total } else emptyMap()
        cache[recipeId] = shares
        return shares
    }

    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SENT = "SENT"
        const val STATUS_FAILED = "FAILED"
        const val MAX_ATTEMPTS = 5
        private const val SECRET_BYTES = 32
        private const val HTTP_NOT_FOUND = 404

        /** Cycles that end this week can have started up to this many days before it. */
        private const val CYCLE_LOOKBACK_DAYS = 70L
    }
}
