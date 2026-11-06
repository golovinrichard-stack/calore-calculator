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
