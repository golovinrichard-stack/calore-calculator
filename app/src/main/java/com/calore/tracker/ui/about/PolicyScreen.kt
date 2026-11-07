package com.calore.tracker.ui.about

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calore.tracker.R

/** The full privacy policy (M10.1), as shipped in the app. Only in English until it is translated (owner input #4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val czech = LocalConfiguration.current.locales[0].language == "cs"
    val blocks = remember { context.assets.open(POLICY_ASSET).use { Markdown.parse(it.readBytes().toString(Charsets.UTF_8)) } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.policy_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (czech) item { Text(stringResource(R.string.policy_english_only), fontStyle = FontStyle.Italic) }
            items(blocks) { block ->
                when (block) {
                    is MdBlock.Heading -> Text(
                        block.text,
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge
                            else -> MaterialTheme.typography.titleMedium
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    is MdBlock.Paragraph -> Text(block.text, style = MaterialTheme.typography.bodyMedium)
                    is MdBlock.Bullet -> Row {
                        Text("•", modifier = Modifier.width(16.dp))
                        Text(block.text, style = MaterialTheme.typography.bodyMedium)
                    }
                    is MdBlock.Quote -> Text(block.text, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, modifier = Modifier.padding(start = 12.dp))
                    is MdBlock.TableRow -> Text(
                        block.cells.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (block.header) FontWeight.Bold else FontWeight.Normal,
                    )
                    is MdBlock.Code -> Text(
                        block.text,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

/** A copy of docs/PRIVACY_POLICY.md; a unit test keeps them the same. */
const val POLICY_ASSET = "legal/privacy-policy-en.md"
