package com.calore.tracker.domain

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Parses an ISO-8601 date such as 2026-10-21. Returns null for missing or malformed text. */
fun parseIsoDate(text: String?): LocalDate? {
    if (text == null) return null
    return try {
        LocalDate.parse(text)
    } catch (e: DateTimeParseException) {
        null
    }
}
