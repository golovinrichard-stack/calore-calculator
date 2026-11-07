package com.calore.tracker.ui.cycle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.domain.cycle.AppLock
import com.calore.tracker.domain.cycle.Contraception
import com.calore.tracker.domain.cycle.CyclePhase
import com.calore.tracker.domain.cycle.CycleSettings
import com.calore.tracker.domain.cycle.CycleTip
import com.calore.tracker.domain.cycle.CycleToday
import com.calore.tracker.domain.cycle.Cycles
import com.calore.tracker.domain.cycle.Flow
import com.calore.tracker.domain.cycle.Menopause
import com.calore.tracker.domain.cycle.Symptom
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.components.NumberField
import com.calore.tracker.ui.components.SectionHeader
import com.calore.tracker.ui.rememberMediumDateFormatter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

/** Me → Cycle (M7): intro and switch, calendar, logging, tips, settings and privacy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleScreen(
    onBack: () -> Unit,
    lockState: AppLockState,
    viewModel: CycleViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unlocked by lockState.unlocked.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cycle_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        if (state.settings.enabled && state.settings.appLock != AppLock.OFF && !unlocked) {
            LockedContent(lockState, Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.settings.enabled) {
                Intro(onSwitchOn = { viewModel.setEnabled(true) })
            } else {
                CycleTodayText(state.todayInfo, state.settings, style = MaterialTheme.typography.titleMedium)
                if (state.settings.paused) {
                    Text(stringResource(R.string.cycle_paused), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TipsCard(state.tips)
                Calendar(state, viewModel)
                DayActions(state, viewModel)
                HorizontalDivider()
                SettingsSection(state.settings, viewModel)
                Text(stringResource(R.string.cycle_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Intro(onSwitchOn: () -> Unit) {
    Text(stringResource(R.string.cycle_intro_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.cycle_intro_what))
    Text(stringResource(R.string.cycle_intro_private))
    Text(stringResource(R.string.cycle_disclaimer), fontWeight = FontWeight.Medium)
    Button(onClick = onSwitchOn) { Text(stringResource(R.string.cycle_switch_on)) }
}

/** "Day 18 · luteal phase · next period in 9–11 days" (M7.3). */
@Composable
fun CycleTodayText(info: CycleToday?, settings: CycleSettings, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge) {
    if (info == null) {
        Text(stringResource(R.string.cycle_no_period_yet), style = style)
        return
    }
    val parts = buildList {
        add(stringResource(R.string.cycle_day_n, info.day))
        info.phase?.let { add(stringResource(it.labelRes())) }
        if (!settings.paused) {
            add(
                when {
                    info.nextInTo < 0 -> stringResource(R.string.cycle_period_late)
                    info.nextInFrom <= 0 -> stringResource(R.string.cycle_period_any_day)
                    else -> pluralStringResource(R.plurals.cycle_next_in, info.nextInTo.toInt(), info.nextInFrom, info.nextInTo)
                },
            )
        }
    }
    Text(parts.joinToString(" · "), style = style)
}

fun CyclePhase.labelRes(): Int = when (this) {
    CyclePhase.PERIOD -> R.string.cycle_phase_period
    CyclePhase.FOLLICULAR -> R.string.cycle_phase_follicular
    CyclePhase.OVULATION -> R.string.cycle_phase_ovulation
    CyclePhase.LUTEAL -> R.string.cycle_phase_luteal
}

fun CycleTip.labelRes(): Int = when (this) {
    CycleTip.IRON_WITH_VITAMIN_C -> R.string.cycle_tip_iron
    CycleTip.WARM_EASY_MEALS -> R.string.cycle_tip_warm
    CycleTip.PROTEIN_FIBRE_SNACKS -> R.string.cycle_tip_snacks
    CycleTip.MAGNESIUM_FOOD -> R.string.cycle_tip_magnesium
}

@Composable
fun TipsCard(tips: List<CycleTip>) {
    if (tips.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.cycle_tips_title), style = MaterialTheme.typography.titleSmall)
            tips.forEach { Text("•  " + stringResource(it.labelRes()), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

private val periodColour = Color(0xFFE57373)

@Composable
private fun DayMark.colour(): Color = when (this) {
    DayMark.LOGGED_PERIOD -> periodColour
    DayMark.PREDICTED_PERIOD -> periodColour.copy(alpha = 0.3f)
    DayMark.FOLLICULAR -> Color(0xFF81C784).copy(alpha = 0.35f)
    DayMark.OVULATION -> Color(0xFF64B5F6).copy(alpha = 0.4f)
    DayMark.LUTEAL -> Color(0xFFBA68C8).copy(alpha = 0.3f)
    DayMark.NONE -> Color.Transparent
}

private fun DayMark.labelRes(): Int? = when (this) {
    DayMark.LOGGED_PERIOD -> R.string.cycle_legend_period
    DayMark.PREDICTED_PERIOD -> R.string.cycle_legend_predicted
    DayMark.FOLLICULAR -> R.string.cycle_phase_follicular
    DayMark.OVULATION -> R.string.cycle_phase_ovulation
    DayMark.LUTEAL -> R.string.cycle_phase_luteal
    DayMark.NONE -> null
}

/** Month calendar (M7.3): logged period days, the predicted next period (lighter) and phases with a legend. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Calendar(state: CycleUiState, viewModel: CycleViewModel) {
    val locale = LocalConfiguration.current.locales[0]
    val bleeding = state.bleedingDays
    val predicted = Cycles.nextRange(state.periods, state.settings)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.showMonth(state.month.minusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.cycle_previous_month))
            }
            Text(
                state.month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } + " " + state.month.year,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.showMonth(state.month.plusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.cycle_next_month))
            }
        }
        Row {
            DayOfWeek.entries.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        val first = state.month.atDay(1)
        val lead = first.dayOfWeek.value - 1
        val cells = lead + state.month.lengthOfMonth()
        val weeks = (cells + 6) / 7
        for (week in 0 until weeks) {
            Row {
                for (col in 0 until 7) {
                    val index = week * 7 + col - lead
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (index in 0 until state.month.lengthOfMonth()) {
                            val date = first.plusDays(index.toLong())
                            val mark = state.mark(date, bleeding, predicted)
                            val selected = date == state.selected
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(mark.colour(), CircleShape)
                                    .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                    .clickable { viewModel.select(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    date.dayOfMonth.toString(),
                                    color = if (mark == DayMark.LOGGED_PERIOD) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (date == state.today) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (state.days[date]?.let { it.symptoms.isNotEmpty() || it.note != null } == true) {
                                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).size(4.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                }
                            }
                        }
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DayMark.entries.filter { it != DayMark.NONE && (!state.settings.paused || it == DayMark.LOGGED_PERIOD) }.forEach { mark ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(12.dp).background(mark.colour(), CircleShape))
                    Text(stringResource(mark.labelRes()!!), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** Period started / ended and logging for the selected day. */
@Composable
private fun DayActions(state: CycleUiState, viewModel: CycleViewModel) {
    val formatter = rememberMediumDateFormatter()
    var logging by rememberSaveable { mutableStateOf(false) }
    val date = state.selected
    val future = date.isAfter(state.today)
    Text(date.format(formatter), style = MaterialTheme.typography.titleSmall)
    state.days[date]?.let { day ->
        val parts = listOfNotNull(
            day.flow?.let { stringResource(it.labelRes()) },
            day.symptoms.takeIf { it.isNotEmpty() }?.map { stringResource(it.labelRes()) }?.joinToString(", "),
            day.note,
        )
        if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
    }
    val startedHere = state.periods.firstOrNull { it.start == date }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (startedHere == null) {
            Button(onClick = { viewModel.periodStarted(date) }, enabled = !future) { Text(stringResource(R.string.cycle_period_started)) }
        } else {
            OutlinedButton(onClick = { viewModel.deletePeriod(startedHere) }) { Text(stringResource(R.string.cycle_period_remove)) }
        }
        val running = state.periods.filter { !it.start.isAfter(date) }.maxByOrNull { it.start }
        OutlinedButton(onClick = { viewModel.periodEnded(date) }, enabled = !future && running != null && running.end != date) {
            Text(stringResource(R.string.cycle_period_ended))
        }
    }
    OutlinedButton(onClick = { logging = true }, enabled = !future) { Text(stringResource(R.string.cycle_log_day)) }
    if (logging) {
        LogDayDialog(state, date, onSave = { flow, symptoms, note ->
            logging = false
            viewModel.logDay(date, flow, symptoms, note)
        }, onDismiss = { logging = false })
    }
}

fun Flow.labelRes(): Int = when (this) {
    Flow.NONE -> R.string.cycle_flow_none
    Flow.LIGHT -> R.string.cycle_flow_light
    Flow.MEDIUM -> R.string.cycle_flow_medium
    Flow.HEAVY -> R.string.cycle_flow_heavy
}

fun Symptom.labelRes(): Int = when (this) {
    Symptom.CRAMPS -> R.string.cycle_symptom_cramps
    Symptom.BLOATING -> R.string.cycle_symptom_bloating
    Symptom.CRAVINGS -> R.string.cycle_symptom_cravings
    Symptom.LOW_MOOD -> R.string.cycle_symptom_low_mood
    Symptom.LOW_ENERGY -> R.string.cycle_symptom_low_energy
    Symptom.POOR_SLEEP -> R.string.cycle_symptom_poor_sleep
    Symptom.HEADACHE -> R.string.cycle_symptom_headache
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogDayDialog(state: CycleUiState, date: LocalDate, onSave: (Flow?, Set<Symptom>, String?) -> Unit, onDismiss: () -> Unit) {
    val existing = state.days[date]
    var flow by remember { mutableStateOf(existing?.flow) }
    var symptoms by remember { mutableStateOf(existing?.symptoms.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cycle_log_day)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(stringResource(R.string.cycle_flow))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Flow.entries.forEach { option ->
                        FilterChip(selected = flow == option, onClick = { flow = if (flow == option) null else option }, label = { Text(stringResource(option.labelRes())) })
                    }
                }
                SectionHeader(stringResource(R.string.cycle_symptoms))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Symptom.entries.forEach { option ->
                        FilterChip(
                            selected = option in symptoms,
                            onClick = { symptoms = if (option in symptoms) symptoms - option else symptoms + option },
                            label = { Text(stringResource(option.labelRes())) },
                        )
                    }
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.cycle_note)) }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.cycle_note_private), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(flow, symptoms, note) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** M7.1 settings and M7.6 privacy. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsSection(settings: CycleSettings, viewModel: CycleViewModel) {
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var noScreenLock by rememberSaveable { mutableStateOf(false) }
    var cycleLength by rememberSaveable(settings.defaultCycleLength) { mutableStateOf(settings.defaultCycleLength.toString()) }
    var periodLength by rememberSaveable(settings.defaultPeriodLength) { mutableStateOf(settings.defaultPeriodLength.toString()) }
    Text(stringResource(R.string.cycle_settings), style = MaterialTheme.typography.titleMedium)
    SectionHeader(stringResource(R.string.cycle_contraception))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Contraception.entries.forEach { option ->
            FilterChip(selected = settings.contraception == option, onClick = { viewModel.saveSettings(settings.copy(contraception = option)) }, label = { Text(stringResource(option.labelRes())) })
        }
    }
    SwitchRow(stringResource(R.string.cycle_pregnant), settings.pregnant) { viewModel.saveSettings(settings.copy(pregnant = it)) }
    SwitchRow(stringResource(R.string.cycle_breastfeeding), settings.breastfeeding) { viewModel.saveSettings(settings.copy(breastfeeding = it)) }
    SectionHeader(stringResource(R.string.cycle_menopause))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Menopause.entries.forEach { option ->
            FilterChip(selected = settings.menopause == option, onClick = { viewModel.saveSettings(settings.copy(menopause = option)) }, label = { Text(stringResource(option.labelRes())) })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(
            value = cycleLength,
            onValueChange = { text ->
                cycleLength = text
                text.toIntOrNull()?.takeIf { it in CycleSettings.CYCLE_LENGTHS }?.let { viewModel.saveSettings(settings.copy(defaultCycleLength = it)) }
            },
            label = stringResource(R.string.cycle_usual_length),
            decimal = false,
            modifier = Modifier.weight(1f),
        )
        NumberField(
            value = periodLength,
            onValueChange = { text ->
                periodLength = text
                text.toIntOrNull()?.takeIf { it in CycleSettings.PERIOD_LENGTHS }?.let { viewModel.saveSettings(settings.copy(defaultPeriodLength = it)) }
            },
            label = stringResource(R.string.cycle_usual_period),
            decimal = false,
            modifier = Modifier.weight(1f),
        )
    }
    Text(stringResource(R.string.cycle_lengths_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    SectionHeader(stringResource(R.string.cycle_privacy))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppLock.entries.forEach { option ->
            FilterChip(
                selected = settings.appLock == option,
                onClick = {
                    if (option != AppLock.OFF && !AppLocks.canLock(context)) noScreenLock = true else viewModel.saveSettings(settings.copy(appLock = option))
                },
                label = { Text(stringResource(option.labelRes())) },
            )
        }
    }
    if (noScreenLock) Text(stringResource(R.string.lock_needs_screen_lock), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.cycle_private_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.cycle_delete_all), color = MaterialTheme.colorScheme.error) }
    OutlinedButton(onClick = { viewModel.setEnabled(false) }) { Text(stringResource(R.string.cycle_switch_off)) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.cycle_delete_all_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteAll()
                }) { Text(stringResource(R.string.cycle_delete_all), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun Contraception.labelRes(): Int = when (this) {
    Contraception.NONE -> R.string.cycle_contraception_none
    Contraception.PILL -> R.string.cycle_contraception_pill
    Contraception.HORMONAL_IUD -> R.string.cycle_contraception_hormonal_iud
    Contraception.COPPER_IUD -> R.string.cycle_contraception_copper_iud
    Contraception.IMPLANT -> R.string.cycle_contraception_implant
    Contraception.OTHER_HORMONAL -> R.string.cycle_contraception_other
}

private fun Menopause.labelRes(): Int = when (this) {
    Menopause.NO -> R.string.cycle_menopause_no
    Menopause.PERIMENOPAUSE -> R.string.cycle_menopause_peri
    Menopause.MENOPAUSE -> R.string.cycle_menopause_yes
}

private fun AppLock.labelRes(): Int = when (this) {
    AppLock.OFF -> R.string.lock_off
    AppLock.CYCLE -> R.string.lock_cycle
    AppLock.APP -> R.string.lock_app
}
