package com.barteqcz.onqa.ui.onboarding

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.barteqcz.onqa.R

@Composable
fun BackgroundLocationDisclosure(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                shape = RoundedCornerShape(28.dp)
            ),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text(
                text = stringResource(R.string.background_location_disclosure_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            val fullText = stringResource(R.string.background_location_disclosure_message)
            val highlightText = "“Allow all the time”"
            val annotatedString = buildAnnotatedString {
                val startIndex = fullText.indexOf(highlightText)
                if (startIndex != -1) {
                    append(fullText.substring(0, startIndex))
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                        append(highlightText)
                    }
                    append(fullText.substring(startIndex + highlightText.length))
                } else {
                    append(fullText)
                }
            }
            Text(
                text = annotatedString,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.continue_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.not_now), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
