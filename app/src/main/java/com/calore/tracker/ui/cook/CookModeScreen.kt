package com.calore.tracker.ui.cook

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.data.repository.RecipeDetails
import com.calore.tracker.data.repository.RecipeRepository
import com.calore.tracker.domain.CookTimeline
import com.calore.tracker.domain.TimelineStep
import com.calore.tracker.notifications.CookTimers
import com.calore.tracker.notifications.Notifications
import com.calore.tracker.notifications.ReminderScheduler
import com.calore.tracker.notifications.rememberNotificationPermission
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.navigation.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

data class CookModeUiState(
    val recipes: List<RecipeDetails> = emptyList(),
    val timeline: List<TimelineStep> = emptyList(),
    /** Epoch millis when cooking started; null before "Start cooking". */
    val startedAt: Long? = null,
    /** Running timers: "recipe:step" → epoch millis when they ring. */
    val timers: Map<String, Long> = emptyMap(),
)

/** Cook mode (M4.4): one timeline for all recipes of this cook, with timers. */
class CookModeViewModel(
    private val savedStateHandle: SavedStateHandle,
    recipes: RecipeRepository,
    private val reminders: ReminderScheduler,
    private val appPrefs: AppPrefs,
    private val clock: Clock,
) : ViewModel() {

    private val state = MutableStateFlow(CookModeUiState(startedAt = savedStateHandle.get<Long>(KEY_STARTED)))
    val uiState: StateFlow<CookModeUiState> = state.asStateFlow()

    init {
        val ids = savedStateHandle.get<String>(Routes.ARG_RECIPE_IDS).orEmpty().split(',').filter { it.isNotBlank() }
        viewModelScope.launch {
            val details = ids.mapNotNull { recipes.details(it) }
            state.update { it.copy(recipes = details, timeline = CookTimeline.schedule(details.map { d -> d.steps })) }
        }
    }

    fun now(): Long = clock.millis()

    fun start() {
        val now = clock.millis()
        savedStateHandle[KEY_STARTED] = now
        state.update { it.copy(startedAt = now) }
    }

    /** Returns true the first time a timer can't be exact, so the screen explains once. */
    suspend fun shouldExplainExactAlarms(canBeExact: Boolean): Boolean {
        if (canBeExact || appPrefs.settings.first().exactAlarmHintShown) return false
        appPrefs.setExactAlarmHintShown()
        return true
    }

    fun timerStarted(key: String, ringsAt: Long) = state.update { it.copy(timers = it.timers + (key to ringsAt)) }

    /** Ends cook mode and sets the cooling reminder 2 hours later (A12). */
    fun finish() = reminders.scheduleCoolingReminder()

    companion object {
        private const val KEY_STARTED = "started_at"
        fun key(step: TimelineStep) = "${step.recipeIndex}:${step.stepIndex}"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookModeScreen(onBack: () -> Unit, onFinished: () -> Unit, viewModel: CookModeViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val view = LocalView.current
    var nowMillis by remember { mutableLongStateOf(viewModel.now()) }
    var explainExact by remember { mutableStateOf(false) }
    var pendingTimer by remember { mutableStateOf<TimelineStep?>(null) }

    // The screen stays on while cook mode is open.
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = viewModel.now()
            delay(1_000)
        }
    }

    fun startTimer(step: TimelineStep) {
        val minutes = step.step.minutes ?: return
        val ringsAt = viewModel.now() + minutes * 60_000L
        val recipe = state.recipes.getOrNull(step.recipeIndex)?.recipe?.displayName().orEmpty()
        CookTimers.start(
            context,
            Notifications.ID_TIMER_BASE + step.recipeIndex * 100 + step.stepIndex,
            ringsAt,
            resources.getString(R.string.notify_timer_title, recipe),
            step.step.displayText(),
        )
        viewModel.timerStarted(CookModeViewModel.key(step), ringsAt)
    }
    val requestPermission = rememberNotificationPermission { _ ->
        // Timers work without the permission too: the countdown still shows here.
        pendingTimer?.let(::startTimer)
        pendingTimer = null
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cook_mode_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    if (state.startedAt == null) {
                        viewModel.start()
                    } else {
                        viewModel.finish()
                        onFinished()
                    }
                },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            ) { Text(stringResource(if (state.startedAt == null) R.string.cook_start else R.string.cook_finish)) }
        },
    ) { padding ->
        val minute = state.startedAt?.let { ((nowMillis - it) / 60_000L).toInt() } ?: -1
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.cook_total, CookTimeline.totalMinutes(state.timeline)), style = MaterialTheme.typography.titleMedium)
            if (minute >= 0) {
                val now = CookTimeline.now(state.timeline, minute)
                if (now.isNotEmpty()) {
                    StepCard(stringResource(R.string.cook_now), now, state, nowMillis, highlight = true) { step ->
                        scope.launch {
                            if (viewModel.shouldExplainExactAlarms(CookTimers.canBeExact(context))) explainExact = true
                        }
                        if (Notifications.canPost(context)) startTimer(step) else {
                            pendingTimer = step
                            requestPermission()
                        }
                    }
                }
                CookTimeline.next(state.timeline, minute)?.let { next ->
                    StepCard(stringResource(R.string.cook_next_step), listOf(next), state, nowMillis, highlight = false, onTimer = null)
                }
            }
            state.timeline.forEach { step ->
                Row {
                    Text(stringResource(R.string.cook_at_minute, step.start), modifier = Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(state.recipes.getOrNull(step.recipeIndex)?.recipe?.displayName().orEmpty(), style = MaterialTheme.typography.labelMedium)
                        Text(
                            step.step.displayText(),
                            fontWeight = if (minute in step.start until step.end) FontWeight.Bold else FontWeight.Normal,
                            color = if (minute >= step.end) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }

    if (explainExact) {
        AlertDialog(
            onDismissRequest = { explainExact = false },
            text = { Text(stringResource(R.string.cook_exact_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    explainExact = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.fromParts("package", context.packageName, null)),
                            )
                        } catch (e: ActivityNotFoundException) {
                            // Some phones have no such page; the timers still ring, a little later.
                        }
                    }
                }) { Text(stringResource(R.string.cook_exact_allow)) }
            },
            dismissButton = { TextButton(onClick = { explainExact = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun StepCard(
    title: String,
    steps: List<TimelineStep>,
    state: CookModeUiState,
    nowMillis: Long,
    highlight: Boolean,
    onTimer: ((TimelineStep) -> Unit)?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (highlight) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            steps.forEach { step ->
                Text(state.recipes.getOrNull(step.recipeIndex)?.recipe?.displayName().orEmpty(), style = MaterialTheme.typography.labelMedium)
                Text(step.step.displayText(), style = MaterialTheme.typography.titleMedium)
                val ringsAt = state.timers[CookModeViewModel.key(step)]
                when {
                    ringsAt != null -> {
                        val left = ((ringsAt - nowMillis) / 1000).coerceAtLeast(0)
                        Text(
                            if (left == 0L) stringResource(R.string.cook_timer_done) else stringResource(R.string.cook_timer_left, "%d:%02d".format(left / 60, left % 60)),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                    onTimer != null && step.step.minutes != null -> OutlinedButton(onClick = { onTimer(step) }) { Text(stringResource(R.string.cook_start_timer)) }
                }
            }
        }
    }
}
