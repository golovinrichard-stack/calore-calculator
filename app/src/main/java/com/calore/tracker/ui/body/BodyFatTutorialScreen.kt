package com.calore.tracker.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.calore.tracker.R
import kotlinx.coroutines.launch

private data class TutorialPage(val title: Int, val text: Int, val spot: TapeSpot?)

private val pages = listOf(
    TutorialPage(R.string.tutorial_neck_title, R.string.tutorial_neck_text, TapeSpot.NECK),
    TutorialPage(R.string.tutorial_waist_men_title, R.string.tutorial_waist_men_text, TapeSpot.WAIST_MEN),
    TutorialPage(R.string.tutorial_waist_women_title, R.string.tutorial_waist_women_text, TapeSpot.WAIST_WOMEN),
    TutorialPage(R.string.tutorial_hips_title, R.string.tutorial_hips_text, TapeSpot.HIPS),
    TutorialPage(R.string.tutorial_tips_title, R.string.tutorial_tips_text, null),
)

/** How to measure for the US Navy method (M1.4): five short pages. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyFatTutorialScreen(onBack: () -> Unit) {
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tutorial_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
                val page = pages[index]
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        stringResource(R.string.tutorial_page, index + 1, pages.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    page.spot?.let { spot -> TapeDrawing(spot = spot, contentDescription = stringResource(page.title)) }
                    Text(stringResource(page.title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(page.text), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.back)) }
                }
                if (pagerState.currentPage < pages.lastIndex) {
                    Button(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.setup_next)) }
                } else {
                    Button(onClick = onBack, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.tutorial_done)) }
                }
            }
        }
    }
}
