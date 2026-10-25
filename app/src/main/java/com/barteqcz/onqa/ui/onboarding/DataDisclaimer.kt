package com.barteqcz.onqa.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import com.barteqcz.onqa.R

@Composable
fun DataDisclaimer(
    onConfirm: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val alignment = if (isLandscape) Alignment.Start else Alignment.CenterHorizontally
    val textAlign = if (isLandscape) TextAlign.Start else TextAlign.Center

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .systemBarsPadding(),
            horizontalAlignment = alignment,
        ) {
            if (!isLandscape) {
                Spacer(modifier = Modifier.height(128.dp))
            }

            Text(
                text = stringResource(R.string.disclaimer_title),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                textAlign = textAlign
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = alignment
            ) {
                val fullText = stringResource(R.string.disclaimer_message)
                val annotatedString = buildAnnotatedString {
                    append(fullText)

                    val links = listOf(
                        "fmscan.org" to "https://fmscan.org",
                        "onqa.barteq.cz/db" to "https://onqa.barteq.cz/db"
                    )

                    links.forEach { (linkText, url) ->
                        val startIndex = fullText.indexOf(linkText)
                        if (startIndex != -1) {
                            addLink(
                                url = LinkAnnotation.Url(
                                    url = url,
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = MaterialTheme.colorScheme.primary,
                                            textDecoration = TextDecoration.Underline,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                ),
                                start = startIndex,
                                end = startIndex + linkText.length
                            )
                        }
                    }
                }

                Text(
                    text = annotatedString,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = textAlign,
                        lineHeight = 24.sp
                    )
                )

            }

            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = stringResource(R.string.alright),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
