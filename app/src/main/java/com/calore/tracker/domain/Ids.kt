package com.calore.tracker.domain

import java.util.UUID

/**
 * A new random id for a row: a lowercase UUID, 36 characters. Every new row gets its id here,
 * never from SQL, so ids made on different phones can't clash once the household syncs.
 */
fun newId(): String = UUID.randomUUID().toString()
