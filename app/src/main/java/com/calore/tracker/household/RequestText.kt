package com.calore.tracker.household

import android.content.Context
import com.calore.tracker.R
import com.calore.tracker.data.db.RequestEntity
import com.calore.tracker.data.household.RequestStatus
import com.calore.tracker.data.household.RequestType
import com.calore.tracker.data.household.requestStatus
import com.calore.tracker.data.household.requestType
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.household.ActivityLine
import com.calore.tracker.domain.household.ActivityType
import com.calore.tracker.ui.labelRes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Sentences about requests and the feed, made on each phone in its own language (M11.7, M11.8). */
object RequestText {

    private fun date(text: String?): String? = text?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

    /** What is asked, e.g. "Cook for me: 12 Nov 2026, lunch, 2 portions (Chili)". */
    fun what(context: Context, request: RequestEntity, itemName: String?): String = when (request.requestType) {
        RequestType.COOK -> {
            val meal = request.meal?.let { m -> MealType.entries.firstOrNull { it.name == m } }?.let { context.getString(it.labelRes()) }.orEmpty()
            val base = context.resources.getQuantityString(
                R.plurals.request_cook_what,
                request.portions ?: 1,
                date(request.dueDate).orEmpty(),
                meal,
                request.portions ?: 1,
            )
            if (itemName != null) "$base ($itemName)" else base
        }
        RequestType.BUY -> context.getString(R.string.request_buy_what, itemName.orEmpty())
        RequestType.CAN_I_HAVE -> context.getString(R.string.request_can_i_have_what, itemName.orEmpty())
        RequestType.OTHER -> request.message.orEmpty()
    }

    fun status(context: Context, request: RequestEntity): String = context.getString(
        when (request.requestStatus) {
            RequestStatus.OPEN -> R.string.request_status_open
            RequestStatus.ACCEPTED -> if (request.requestType == RequestType.CAN_I_HAVE) R.string.request_status_yes else R.string.request_status_accepted
            RequestStatus.DECLINED -> if (request.requestType == RequestType.CAN_I_HAVE) R.string.request_status_no else R.string.request_status_declined
            RequestStatus.DONE -> R.string.request_status_done
            RequestStatus.CANCELLED -> R.string.request_status_cancelled
        },
    )

    /** "Anna bought eggs and milk", "Tom cooked Chili (6 boxes)". */
    fun activity(context: Context, line: ActivityLine, who: String): String {
        val names = joinNames(context, line.names)
        return when (line.type) {
            ActivityType.BOUGHT -> context.getString(R.string.activity_bought, who, names)
            ActivityType.COOKED -> line.count?.let { context.resources.getQuantityString(R.plurals.activity_cooked_boxes, it, who, names, it) }
                ?: context.getString(R.string.activity_cooked, who, names)
            ActivityType.TOOK -> context.getString(R.string.activity_took, who, names)
            ActivityType.THREW_AWAY -> context.getString(R.string.activity_threw_away, who, names)
            ActivityType.REQUESTED -> if (names.isEmpty()) context.getString(R.string.activity_requested_any, who) else context.getString(R.string.activity_requested, who, names)
            ActivityType.ANSWERED -> context.getString(R.string.activity_answered, who)
        }
    }

    private fun joinNames(context: Context, names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> names[0]
        else -> context.getString(R.string.list_and, names.dropLast(1).joinToString(", "), names.last())
    }

    /** The whole request as plain text, for "Send as a message" (M11.10). */
    fun asMessage(context: Context, request: RequestEntity, itemName: String?, from: String): String =
        listOfNotNull(context.getString(R.string.request_message_from, from), what(context, request, itemName), request.message).joinToString("\n")
}
