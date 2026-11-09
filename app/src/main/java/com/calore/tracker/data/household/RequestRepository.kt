package com.calore.tracker.data.household

import com.calore.tracker.data.db.HouseholdDao
import com.calore.tracker.data.db.RequestEntity
import com.calore.tracker.data.repository.CookingRepository
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.repository.PantryRepository
import com.calore.tracker.data.repository.PlanRepository
import com.calore.tracker.data.repository.ShoppingRepository
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.SlotKind
import com.calore.tracker.domain.household.ActivityType
import com.calore.tracker.domain.newId
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class RequestType { COOK, BUY, CAN_I_HAVE, OTHER }

enum class RequestStatus { OPEN, ACCEPTED, DECLINED, DONE, CANCELLED }

val RequestEntity.requestType: RequestType get() = RequestType.entries.firstOrNull { it.name == type } ?: RequestType.OTHER
val RequestEntity.requestStatus: RequestStatus get() = RequestStatus.entries.firstOrNull { it.name == status } ?: RequestStatus.OPEN

/**
 * Requests between members (M11.7): cook for me, buy for me, can I have it? A request is a row of
 * the shared `requests` table, so it reaches the others with the next sync, and so does the answer.
 */
class RequestRepository(
    private val dao: HouseholdDao,
    private val members: MemberRepository,
    private val cooking: CookingRepository,
    private val pantry: PantryRepository,
    private val shopping: ShoppingRepository,
    private val plans: PlanRepository,
    private val activity: ActivityLog,
    private val clock: Clock,
) {
    val requests: Flow<List<RequestEntity>> = dao.observeRequests()

    private suspend fun ask(
        type: RequestType,
        to: String?,
        refTable: String? = null,
        refId: String? = null,
        message: String? = null,
        dueDate: LocalDate? = null,
        meal: MealType? = null,
        portions: Int? = null,
        what: String,
    ): RequestEntity {
        val me = members.ensureMe()
        val now = clock.millis()
        val request = RequestEntity(
            id = newId(),
            type = type.name,
            fromMemberId = me.id,
            toMemberId = to,
            refTable = refTable,
            refId = refId,
            message = message?.trim()?.takeIf { it.isNotEmpty() },
            dueDate = dueDate?.toString(),
            meal = meal?.name,
            portions = portions,
            status = RequestStatus.OPEN.name,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertRequest(request)
        activity.log(ActivityType.REQUESTED, "requests", request.id, what)
        return request
    }

    /** "Cook for me": a day, a meal, maybe a recipe, and portions. */
    suspend fun cookForMe(to: String?, date: LocalDate, meal: MealType, recipeId: String?, recipeName: String?, portions: Int, message: String?) =
        ask(RequestType.COOK, to, recipeId?.let { "recipes" }, recipeId, message, date, meal, portions, recipeName.orEmpty())

    /** "Buy for me": the item goes on the shared shopping list straight away, with the requester's name. */
    suspend fun buyForMe(name: String, message: String?): RequestEntity {
        val me = members.ensureMe()
        val itemId = shopping.addManual(name, requestedBy = me.id)
        return ask(RequestType.BUY, null, "shopping_items", itemId, message, what = name.trim())
    }

    /** "Can I have it?": a box or pantry item with someone else's "Mine" label. */
    suspend fun canIHave(refTable: String, refId: String, owner: String?, name: String, message: String? = null) =
        ask(RequestType.CAN_I_HAVE, owner, refTable, refId, message, what = name)

    /**
     * An answer. Accepting "cook for me" puts it in the cook's plan; "yes" to "can I have it?"
     * gives the item to the person who asked.
     */
    suspend fun answer(request: RequestEntity, status: RequestStatus, message: String? = null) {
        val now = clock.millis()
        val updated = request.copy(status = status.name, message = message?.trim()?.takeIf { it.isNotEmpty() } ?: request.message, updatedAt = now)
        dao.upsertRequest(updated)
        if (status == RequestStatus.ACCEPTED) {
            when (request.requestType) {
                RequestType.COOK -> addToPlan(request)
                RequestType.CAN_I_HAVE -> giveItem(request)
                else -> Unit
            }
        }
        activity.log(ActivityType.ANSWERED, "requests", request.id, status.name)
    }

    /** "Suggest another time" for "cook for me": the request stays open with the new day. */
    suspend fun suggest(request: RequestEntity, date: LocalDate, meal: MealType, message: String?) {
        dao.upsertRequest(
            request.copy(
                dueDate = date.toString(),
                meal = meal.name,
                message = message?.trim()?.takeIf { it.isNotEmpty() } ?: request.message,
                updatedAt = clock.millis(),
            ),
        )
        activity.log(ActivityType.ANSWERED, "requests", request.id, date.toString())
    }

    suspend fun cancel(request: RequestEntity) {
        dao.upsertRequest(request.copy(status = RequestStatus.CANCELLED.name, updatedAt = clock.millis()))
    }

    private suspend fun addToPlan(request: RequestEntity) {
        val date = request.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        val meal = request.meal?.let { m -> MealType.entries.firstOrNull { it.name == m } } ?: return
        val planId = plans.planFor(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
        val recipeId = request.refId.takeIf { request.refTable == "recipes" }
        plans.setSlot(planId, date, meal, request.fromMemberId, SlotKind.COOK_FRESH, recipeId, null)
    }

    private suspend fun giveItem(request: RequestEntity) {
        val id = request.refId ?: return
        when (request.refTable) {
            "boxes" -> cooking.box(id)?.let { cooking.giveTo(it, request.fromMemberId) }
            "pantry_items" -> pantry.item(id)?.let { pantry.setOwnership(it, request.fromMemberId, it.visibility) }
        }
    }
}
