package com.calore.tracker.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.backup.BackupCounts
import com.calore.tracker.data.backup.BackupFile
import com.calore.tracker.data.backup.BackupReadResult
import com.calore.tracker.data.backup.BackupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeParseException

enum class BackupWork { EXPORTING, READING, IMPORTING }

/** A checked backup waiting for the user to confirm that it may replace their data. */
data class PendingImport(
    val counts: BackupCounts,
    val exportedAt: Instant?,
    internal val backup: BackupFile,
)

data class BackupUiState(
    val work: BackupWork? = null,
    val pendingImport: PendingImport? = null,
) {
    val isBusy: Boolean get() = work != null
}

sealed interface BackupMessage {
    data class Exported(val counts: BackupCounts) : BackupMessage
    data class Imported(val counts: BackupCounts) : BackupMessage
    data object ExportFailed : BackupMessage
    data object ReadFailed : BackupMessage
    data object NotABackup : BackupMessage
    data object NewerVersion : BackupMessage
    data object Damaged : BackupMessage
    data object ImportFailed : BackupMessage
}

class BackupViewModel(private val repository: BackupRepository) : ViewModel() {

    private val state = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = state.asStateFlow()

    private val messageChannel = Channel<BackupMessage>(Channel.BUFFERED)
    val messages: Flow<BackupMessage> = messageChannel.receiveAsFlow()

    fun defaultFileName(): String = repository.defaultFileName()

    fun export(uri: Uri) = runWork(BackupWork.EXPORTING) {
        val message = try {
            BackupMessage.Exported(repository.export(uri))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Any failure of the storage provider; the user can pick another place.
            BackupMessage.ExportFailed
        }
        messageChannel.send(message)
    }

    /** Reads and checks [uri]; if it is a usable backup, asks for confirmation via [BackupUiState.pendingImport]. */
    fun open(uri: Uri) = runWork(BackupWork.READING) {
        val result = try {
            repository.read(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            messageChannel.send(BackupMessage.ReadFailed)
            return@runWork
        }
        when (result) {
            is BackupReadResult.Ok -> state.update {
                it.copy(
                    pendingImport = PendingImport(
                        counts = result.backup.tables.counts,
                        exportedAt = parseInstant(result.backup.exportedAt),
                        backup = result.backup,
                    ),
                )
            }
            BackupReadResult.NotABackup -> messageChannel.send(BackupMessage.NotABackup)
            is BackupReadResult.NewerVersion -> messageChannel.send(BackupMessage.NewerVersion)
            BackupReadResult.Damaged -> messageChannel.send(BackupMessage.Damaged)
        }
    }

    fun cancelImport() {
        state.update { it.copy(pendingImport = null) }
    }

    fun confirmImport() {
        val pending = state.value.pendingImport ?: return
        state.update { it.copy(pendingImport = null) }
        runWork(BackupWork.IMPORTING) {
            val message = try {
                repository.restore(pending.backup)
                BackupMessage.Imported(pending.counts)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The import runs in one transaction, so the old data is untouched.
                BackupMessage.ImportFailed
            }
            messageChannel.send(message)
        }
    }

    private fun runWork(work: BackupWork, block: suspend () -> Unit) {
        if (state.value.isBusy) return
        state.update { it.copy(work = work) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                state.update { it.copy(work = null) }
            }
        }
    }

    private fun parseInstant(text: String): Instant? = try {
        Instant.parse(text)
    } catch (e: DateTimeParseException) {
        null
    }
}
