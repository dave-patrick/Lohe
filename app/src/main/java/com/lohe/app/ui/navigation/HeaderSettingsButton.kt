package com.lohe.app.ui.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lohe.app.R

/**
 * The settings control, in the header of every screen.
 *
 * It used to sit on the home screen alone, so reaching it from anywhere else
 * meant finding a way back to the home screen first.
 *
 * It is a plain icon rather than a filled disc, because the other controls
 * in a header are flat outlines and a filled circle next to them looked like
 * it belonged to something else.
 */
@Composable
fun HeaderSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(40.dp)) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = stringResource(R.string.settings_title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}
