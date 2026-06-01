package com.barteqcz.onqa.ui.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.window.DialogProperties
import com.barteqcz.onqa.R

@Composable
fun BackgroundLocationDisclosure(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = true),
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text(
                text = stringResource(R.string.background_location_disclosure_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            val fullText = stringResource(R.string.background_location_disclosure_message)
            val highlightText = "“Allow all the time”"
            val annotatedString = buildAnnotatedString {
                val startIndex = fullText.indexOf(highlightText)
                if (startIndex > -1) {
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
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.continue_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.not_now), color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1C1C1E),
        titleContentColor = Color.White,
        textContentColor = Color.LightGray
    )
}
