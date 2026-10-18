package com.lohe.app.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lohe.app.R

/**
 * About.
 *
 * The three brand assets — hero mark, wordmark, studio logo — carry this page,
 * so the copy stays out of their way and only supplies what the artwork cannot:
 * the version, the meaning of the name, and who made the thing.
 *
 * The wordmark and studio logo are white/cyan marks built for the dark palette.
 * In light mode they would sit invisible on paper white, so they fall back to
 * text rather than rendering a gap.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val isLight = MaterialTheme.colorScheme.background.luminanceIsLight()

    // Read from the installed package rather than a baked-in constant, so the
    // page cannot drift from the APK it ships in.
    val version = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            Text(
                text = stringResource(R.string.about_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Hero mark. The artwork carries its own dark plate, so it reads the
        // same in either appearance.
        Image(
            painter = painterResource(id = R.drawable.brand_mark),
            contentDescription = stringResource(R.string.about_mark_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
        )

        Spacer(Modifier.height(12.dp))

        if (isLight) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.wordmark),
                contentDescription = stringResource(R.string.about_wordmark_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = if (version != null) {
                stringResource(R.string.about_version, version)
            } else {
                stringResource(R.string.about_version_unknown)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(4.dp))

        AboutBlock(
            heading = stringResource(R.string.about_name_heading),
            body = stringResource(R.string.about_name_body),
        )

        AboutBlock(
            heading = stringResource(R.string.about_credits_heading),
            body = stringResource(R.string.about_studio),
        )

        Spacer(Modifier.height(20.dp))

        // Studio logo, same light-mode caveat as the wordmark above.
        if (isLight) {
            Text(
                text = "Atomic 22 Industries",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.atomic22),
                contentDescription = stringResource(R.string.about_studio_logo_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(64.dp),
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.about_copyright),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun AboutBlock(heading: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
    ) {
        Text(
            text = heading,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Perceived lightness of the current background.
 *
 * Derived from the theme rather than the [com.lohe.app.ui.theme.Appearance]
 * enum so the page follows whatever the user actually has in front of them.
 */
private fun androidx.compose.ui.graphics.Color.luminanceIsLight(): Boolean {
    val l = (red * 0.2126f + green * 0.7152f + blue * 0.0722f)
    return l > 0.5f
}