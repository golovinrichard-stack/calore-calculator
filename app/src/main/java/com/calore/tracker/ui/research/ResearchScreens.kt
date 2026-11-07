package com.calore.tracker.ui.research

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.data.repository.ResearchRepository
import com.calore.tracker.domain.research.ResearchCategory
import com.calore.tracker.domain.research.ResearchReports
import com.calore.tracker.ui.AppViewModelProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Me → Help research (M10.2): intro, age, choosing, agreeing; afterwards the status screen (M10.5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResearchScreen(
    onBack: () -> Unit,
    onOpenPolicy: () -> Unit,
    onOpenReports: () -> Unit,
    viewModel: ResearchViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.research_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.showStatus) {
                Status(state, viewModel, onOpenReports)
            } else {
                when (state.step) {
                    ResearchStep.INTRO -> Intro(onContinue = viewModel::next, onOpenPolicy = onOpenPolicy)
                    ResearchStep.AGE -> Age(onAnswer = viewModel::answerAge)
                    ResearchStep.UNDER_18 -> {
                        Text(stringResource(R.string.research_under_18))
                        OutlinedButton(onClick = viewModel::back) { Text(stringResource(R.string.back)) }
                    }
                    ResearchStep.CHOOSE -> Choose(state, viewModel, onOpenPolicy)
                }
            }
        }
    }
}

@Composable
private fun Intro(onContinue: () -> Unit, onOpenPolicy: () -> Unit) {
    Text(stringResource(R.string.research_intro_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.research_intro_1))
    Text(stringResource(R.string.research_intro_2))
    Text(stringResource(R.string.research_intro_3))
    TextButton(onClick = onOpenPolicy) { Text(stringResource(R.string.research_read_policy)) }
    Button(onClick = onContinue) { Text(stringResource(R.string.research_continue)) }
}

@Composable
private fun Age(onAnswer: (Boolean) -> Unit) {
    Text(stringResource(R.string.research_age_question), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { onAnswer(true) }) { Text(stringResource(R.string.research_yes)) }
        OutlinedButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.research_no)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choose(state: ResearchUiState, viewModel: ResearchViewModel, onOpenPolicy: () -> Unit) {
    Text(stringResource(R.string.research_choose_title), style = MaterialTheme.typography.titleMedium)
    ResearchCategory.entries.forEach { category ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(category.titleRes()), fontWeight = FontWeight.Medium)
                Text(stringResource(category.descriptionRes()), style = MaterialTheme.typography.bodySmall)
                if (category == ResearchCategory.CYCLE) {
                    Text(stringResource(R.string.research_cycle_sensitive), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Switch(
                checked = category in state.chosen,
                onCheckedChange = { viewModel.toggle(category) },
                modifier = Modifier.testTag("research_${category.name}"),
            )
        }
    }
    Text(stringResource(R.string.research_region), fontWeight = FontWeight.Medium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = state.region == null, onClick = { viewModel.setRegion(null) }, label = { Text(stringResource(R.string.research_region_none)) })
        ResearchReports.REGIONS.forEach { code ->
            FilterChip(selected = state.region == code, onClick = { viewModel.setRegion(code) }, label = { Text(stringResource(regionRes(code))) })
        }
    }
    TextButton(onClick = onOpenPolicy) { Text(stringResource(R.string.research_read_policy)) }
    Button(onClick = viewModel::agree, enabled = state.canAgree, modifier = Modifier.fillMaxWidth().testTag("research_agree")) {
        Text(stringResource(R.string.research_agree))
    }
}

@Composable
private fun Status(state: ResearchUiState, viewModel: ResearchViewModel, onOpenReports: () -> Unit) {
    var confirmStop by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val consent = state.consent
    when {
        consent.pendingDeletion -> Text(stringResource(R.string.research_waiting_delete), style = MaterialTheme.typography.titleMedium)
        consent.withdrawnAt != null -> Text(stringResource(R.string.research_stopped))
        else -> {
            val kinds = consent.categories.sortedBy { it.ordinal }.map { stringResource(it.titleRes()) }.joinToString(", ")
            Text(stringResource(R.string.research_status_kinds, kinds))
            Text(state.lastSentWeek?.let { stringResource(R.string.research_status_last, it) } ?: stringResource(R.string.research_status_none_yet))
        }
    }
    consent.participantId?.let { id ->
        Text(stringResource(R.string.research_status_id), style = MaterialTheme.typography.bodySmall)
        Text(id, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.research_status_write_down), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    OutlinedButton(onClick = onOpenReports) { Text(stringResource(R.string.research_see_reports)) }
    if (consent.active) OutlinedButton(onClick = { confirmStop = true }) { Text(stringResource(R.string.research_stop)) }
    if (consent.participantId != null && !consent.pendingDeletion) {
        OutlinedButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.research_delete), color = MaterialTheme.colorScheme.error) }
    }
    if (consent.participantId == null && !consent.pendingDeletion) {
        Button(onClick = viewModel::startAgain) { Text(stringResource(R.string.research_take_part_again)) }
    }
    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            text = { Text(stringResource(R.string.research_stop_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    viewModel.stop()
                }) { Text(stringResource(R.string.research_stop)) }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.research_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteFromServer()
                }) { Text(stringResource(R.string.research_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** "See what we send" (M10.4): every report with its week, status and full JSON. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResearchReportsScreen(onBack: () -> Unit, viewModel: ResearchViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.research_see_reports)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (state.loaded && state.reports.isEmpty()) {
                item { Text(stringResource(R.string.research_no_reports), modifier = Modifier.padding(16.dp)) }
            }
            items(state.reports, key = { it.id }) { report ->
                ListItem(
                    headlineContent = { Text(report.isoWeek) },
                    supportingContent = { Text(stringResource(statusRes(report.status))) },
                    modifier = Modifier.clickable { open = report.id },
                )
            }
        }
    }
    state.reports.firstOrNull { it.id == open }?.let { report ->
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(report.isoWeek) },
            text = {
                Text(
                    pretty(report.payload),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { open = null }) { Text(stringResource(R.string.close)) } },
        )
    }
}

private val prettyJson = Json { prettyPrint = true }

private fun pretty(payload: String): String =
    runCatching { prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(payload)) }.getOrDefault(payload)

private fun statusRes(status: String): Int = when (status) {
    ResearchRepository.STATUS_SENT -> R.string.research_report_sent
    ResearchRepository.STATUS_FAILED -> R.string.research_report_failed
    else -> R.string.research_report_pending
}

fun ResearchCategory.titleRes(): Int = when (this) {
    ResearchCategory.NUTRITION -> R.string.research_cat_nutrition
    ResearchCategory.BODY -> R.string.research_cat_body
    ResearchCategory.CYCLE -> R.string.research_cat_cycle
    ResearchCategory.SUPPLEMENTS -> R.string.research_cat_supplements
}

private fun ResearchCategory.descriptionRes(): Int = when (this) {
    ResearchCategory.NUTRITION -> R.string.research_cat_nutrition_desc
    ResearchCategory.BODY -> R.string.research_cat_body_desc
    ResearchCategory.CYCLE -> R.string.research_cat_cycle_desc
    ResearchCategory.SUPPLEMENTS -> R.string.research_cat_supplements_desc
}

/** The 14 kraje (ISO 3166-2:CZ). */
private fun regionRes(code: String): Int = when (code) {
    "CZ-10" -> R.string.region_cz10
    "CZ-20" -> R.string.region_cz20
    "CZ-31" -> R.string.region_cz31
    "CZ-32" -> R.string.region_cz32
    "CZ-41" -> R.string.region_cz41
    "CZ-42" -> R.string.region_cz42
    "CZ-51" -> R.string.region_cz51
    "CZ-52" -> R.string.region_cz52
    "CZ-53" -> R.string.region_cz53
    "CZ-63" -> R.string.region_cz63
    "CZ-64" -> R.string.region_cz64
    "CZ-71" -> R.string.region_cz71
    "CZ-72" -> R.string.region_cz72
    else -> R.string.region_cz80
}
