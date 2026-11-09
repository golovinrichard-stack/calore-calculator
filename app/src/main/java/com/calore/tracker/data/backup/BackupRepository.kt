package com.calore.tracker.data.backup

import android.content.ContentResolver
import android.net.Uri
import com.calore.tracker.data.db.BackupDao
import com.calore.tracker.data.db.DatabaseSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Backup to a file and back (M0.5). Files are read and written on [io], never on the main
 * thread, so even a large diary doesn't freeze the screen.
 */
class BackupRepository(
    private val backupDao: BackupDao,
    private val contentResolver: ContentResolver,
    private val clock: Clock,
    private val appVersion: String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /** For example `calore-backup-2026-11-06.json`. */
    fun defaultFileName(): String = "calore-backup-${LocalDate.now(clock)}.json"

    /** Writes every table to the document at [uri]. Throws [IOException] if it can't be written. */
    suspend fun export(uri: Uri): BackupCounts = withContext(io) {
        openForWriting(uri).use { export(it) }
    }

    suspend fun export(output: OutputStream): BackupCounts = withContext(io) {
        val snapshot = backupDao.snapshot()
        val backup = BackupFile(
            format = BackupCodec.FORMAT,
            version = BackupCodec.VERSION,
            exportedAt = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString(),
            appVersion = appVersion,
            tables = BackupTables(
                foods = snapshot.foods.map { it.toRow() },
                diaryEntries = snapshot.diaryEntries.map { it.toRow() },
                weightEntries = snapshot.weightEntries.map { it.toRow() },
                members = snapshot.members.orEmpty().map { it.toRow() },
                bodyMeasurements = snapshot.bodyMeasurements.orEmpty().map { it.toRow() },
                checkins = snapshot.checkins.orEmpty().map { it.toRow() },
                recipes = snapshot.recipes.orEmpty().map { it.toRow() },
                recipeIngredients = snapshot.recipeIngredients.orEmpty().map { it.toRow() },
                recipeSteps = snapshot.recipeSteps.orEmpty().map { it.toRow() },
                pots = snapshot.pots.orEmpty().map { it.toRow() },
                batches = snapshot.batches.orEmpty().map { it.toRow() },
                batchIngredients = snapshot.batchIngredients.orEmpty().map { it.toRow() },
                boxes = snapshot.boxes.orEmpty().map { it.toRow() },
                boxParts = snapshot.boxParts.orEmpty().map { it.toRow() },
                plans = snapshot.plans.orEmpty().map { it.toRow() },
                planSlots = snapshot.planSlots.orEmpty().map { it.toRow() },
                planCooks = snapshot.planCooks.orEmpty().map { it.toRow() },
                pantryItems = snapshot.pantryItems.orEmpty().map { it.toRow() },
                stores = snapshot.stores.orEmpty().map { it.toRow() },
                storeProducts = snapshot.storeProducts.orEmpty().map { it.toRow() },
                prices = snapshot.prices.orEmpty().map { it.toRow() },
                shoppingItems = snapshot.shoppingItems.orEmpty().map { it.toRow() },
                recipeRatings = snapshot.recipeRatings.orEmpty().map { it.toRow() },
                cyclePeriods = snapshot.cyclePeriods.orEmpty().map { it.toRow() },
                cycleDays = snapshot.cycleDays.orEmpty().map { it.toRow() },
                supplements = snapshot.supplements.orEmpty().map { it.toRow() },
                supplementLogs = snapshot.supplementLogs.orEmpty().map { it.toRow() },
                researchReports = snapshot.researchReports.orEmpty().map { it.toRow() },
                requests = snapshot.requests.orEmpty().map { it.toRow() },
                activity = snapshot.activity.orEmpty().map { it.toRow() },
            ),
        )
        output.write(BackupCodec.encode(backup).toByteArray(Charsets.UTF_8))
        output.flush()
        backup.tables.counts
    }

    /** Reads and checks a file without changing anything. Throws [IOException] if it can't be read. */
    suspend fun read(uri: Uri): BackupReadResult = withContext(io) {
        val input = contentResolver.openInputStream(uri) ?: throw IOException("Can't open $uri")
        input.use { read(it) }
    }

    suspend fun read(input: InputStream): BackupReadResult = withContext(io) {
        val bytes = input.readAtMost(BackupCodec.MAX_BYTES) ?: return@withContext BackupReadResult.NotABackup
        BackupCodec.decode(bytes.toString(Charsets.UTF_8))
    }

    /**
     * Replaces all data with [backup], which must come from [read]. Runs in one database
     * transaction: if it fails, the old data is still there and the exception is thrown.
     */
    suspend fun restore(backup: BackupFile) = withContext(io) {
        val tables = backup.tables
        backupDao.replaceAll(
            DatabaseSnapshot(
                foods = tables.foods.map { it.toEntity() },
                diaryEntries = tables.diaryEntries.map { it.toEntity() },
                weightEntries = tables.weightEntries.map { it.toEntity() },
                members = tables.members?.map { it.toEntity() },
                bodyMeasurements = tables.bodyMeasurements?.map { it.toEntity() },
                checkins = tables.checkins?.map { it.toEntity() },
                recipes = tables.recipes?.map { it.toEntity() },
                recipeIngredients = tables.recipeIngredients?.map { it.toEntity() },
                recipeSteps = tables.recipeSteps?.map { it.toEntity() },
                pots = tables.pots?.map { it.toEntity() },
                batches = tables.batches?.map { it.toEntity() },
                batchIngredients = tables.batchIngredients?.map { it.toEntity() },
                boxes = tables.boxes?.map { it.toEntity() },
                boxParts = tables.boxParts?.map { it.toEntity() },
                plans = tables.plans?.map { it.toEntity() },
                planSlots = tables.planSlots?.map { it.toEntity() },
                planCooks = tables.planCooks?.map { it.toEntity() },
                pantryItems = tables.pantryItems?.map { it.toEntity() },
                stores = tables.stores?.map { it.toEntity() },
                storeProducts = tables.storeProducts?.map { it.toEntity() },
                prices = tables.prices?.map { it.toEntity() },
                shoppingItems = tables.shoppingItems?.map { it.toEntity() },
                recipeRatings = tables.recipeRatings?.map { it.toEntity() },
                cyclePeriods = tables.cyclePeriods?.map { it.toEntity() },
                cycleDays = tables.cycleDays?.map { it.toEntity() },
                supplements = tables.supplements?.map { it.toEntity() },
                supplementLogs = tables.supplementLogs?.map { it.toEntity() },
                researchReports = tables.researchReports?.map { it.toEntity() },
                requests = tables.requests?.map { it.toEntity() },
                activity = tables.activity?.map { it.toEntity() },
            ),
        )
    }

    private fun openForWriting(uri: Uri): OutputStream {
        // "wt" truncates an existing file; a few document providers only accept plain "w".
        val stream = try {
            contentResolver.openOutputStream(uri, "wt")
        } catch (e: FileNotFoundException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } ?: contentResolver.openOutputStream(uri, "w")
        return stream ?: throw IOException("Can't write to $uri")
    }
}

/** Reads the whole stream, or returns null as soon as it is longer than [limit] bytes. */
internal fun InputStream.readAtMost(limit: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val read = read(buffer)
        if (read < 0) return out.toByteArray()
        if (out.size() + read > limit) return null
        out.write(buffer, 0, read)
    }
}
