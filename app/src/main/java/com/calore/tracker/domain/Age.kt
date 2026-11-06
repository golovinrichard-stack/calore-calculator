package com.calore.tracker.domain

import java.time.LocalDate
import java.time.Period

/** Whole years between [birthDate] and [today], one less if this year's birthday hasn't come yet (A1). */
fun ageOn(birthDate: LocalDate, today: LocalDate): Int = Period.between(birthDate, today).years

/**
 * A stand-in birth date for someone who only told the app their age: 1 July of (this year − age).
 * The user is asked to confirm it (M1.1).
 */
fun birthDateFromAge(ageYears: Int, today: LocalDate): LocalDate = LocalDate.of(today.year - ageYears, 7, 1)
