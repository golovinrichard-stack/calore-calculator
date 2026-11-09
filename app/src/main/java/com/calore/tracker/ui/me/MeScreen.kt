package com.calore.tracker.ui.me

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.calore.tracker.R
import com.calore.tracker.FeatureFlags

/** Where everything about the user lives (M1.9). Optional features add their rows here later. */
enum class MeDestination(@StringRes val title: Int, @StringRes val description: Int, @DrawableRes val icon: Int) {
    PROFILE(R.string.me_profile, R.string.me_profile_desc, R.drawable.ic_nav_me),
    GOALS(R.string.me_goals, R.string.me_goals_desc, R.drawable.ic_nav_goals),
    BODY(R.string.me_body, R.string.me_body_desc, R.drawable.ic_nav_weight),
    PEOPLE(R.string.people_title, R.string.people_desc, R.drawable.ic_nav_me),
    HOUSEHOLD(R.string.household_title, R.string.household_me_desc, R.drawable.ic_nav_kitchen),
    STATS(R.string.stats_title, R.string.stats_desc, R.drawable.ic_nav_shop),
    CYCLE(R.string.cycle_title, R.string.cycle_me_desc, R.drawable.ic_cycle),
    SUPPLEMENTS(R.string.supplements_title, R.string.supplements_me_desc, R.drawable.ic_supplement),
    RESEARCH(R.string.research_title, R.string.research_me_desc, R.drawable.ic_info),
    SETTINGS(R.string.me_settings, R.string.me_settings_desc, R.drawable.ic_settings),
    ABOUT(R.string.me_about, R.string.me_about_desc, R.drawable.ic_info),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(onOpen: (MeDestination) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.me_title)) }) }) { padding ->
        MeContent(padding = padding, onOpen = onOpen)
    }
}

@Composable
fun MeContent(padding: PaddingValues, onOpen: (MeDestination) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState()),
    ) {
        // M10: "Help research" only exists in builds with a research server address.
        MeDestination.entries.filter {
            (it != MeDestination.RESEARCH || FeatureFlags.research) && (it != MeDestination.HOUSEHOLD || FeatureFlags.household)
        }.forEach { destination ->
            ListItem(
                headlineContent = { Text(stringResource(destination.title)) },
                supportingContent = { Text(stringResource(destination.description)) },
                leadingContent = { Icon(painterResource(destination.icon), contentDescription = null) },
                modifier = Modifier.clickable { onOpen(destination) },
            )
        }
    }
}
