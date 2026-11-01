/**
 * Header UI components displaying current location info with city name and country flag emoji.
 */
package com.barteqcz.onqa.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import android.icu.text.RelativeDateTimeFormatter
import android.os.Build
import android.text.format.DateUtils
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.minutes
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barteqcz.onqa.R
import com.barteqcz.onqa.data.model.LocationInfo

@Composable
fun LocationHeader(
    info: LocationInfo,
    isNetworkAvailable: Boolean,
    lastUpdatedAt: Long?,
) {
    val cityText = info.city ?: stringResource(R.string.unknown_location)
    val relativeTime = rememberRelativeLocationTime(lastUpdatedAt, isNetworkAvailable)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.current_location_header),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )

        AnimatedContent(
            targetState = cityText,
            transitionSpec = {
                fadeIn(tween(600)).togetherWith(
                    fadeOut(tween(600))
                )
            },
            label = "locationTransition"
        ) { targetCity ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val targetCode = info.countryCode
                val flagEmoji = remember(targetCode) {
                    if (targetCode?.length == 2) {
                        targetCode.uppercase().map { char ->
                            Character.toChars(0x1F1E6 + (char - 'A'))
                        }.joinToString("") { String(it) }
                    } else null
                }

                flagEmoji?.let {
                    Text(
                        text = it,
                        fontSize = 20.sp
                    )
                }
                Text(
                    text = targetCity,
                    color = if (isNetworkAvailable) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                relativeTime?.let { time ->
                    Text(
                        text = "($time)",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
fun CompactLocationHeader(
    info: LocationInfo,
    isNetworkAvailable: Boolean,
    lastUpdatedAt: Long?,
    modifier: Modifier = Modifier
) {
    val cityText = info.city ?: stringResource(R.string.unknown_location)
    val relativeTime = rememberRelativeLocationTime(lastUpdatedAt, isNetworkAvailable)
    val targetCode = info.countryCode
    val flagEmoji = remember(targetCode) {
        if (targetCode?.length == 2) {
            targetCode.uppercase().map { char ->
                Character.toChars(0x1F1E6 + (char - 'A'))
            }.joinToString("") { String(it) }
        } else null
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            flagEmoji?.let {
                Text(
                    text = it,
                    fontSize = 16.sp
                )
            }
            Text(
                text = cityText,
                color = if (isNetworkAvailable) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            relativeTime?.let { time ->
                Text(
                    text = "($time)",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun rememberRelativeLocationTime(lastUpdatedAt: Long?, isNetworkAvailable: Boolean): String? {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    LaunchedEffect(lastUpdatedAt, isNetworkAvailable) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1.minutes)
        }
    }
    if (isNetworkAvailable || lastUpdatedAt == null || lastUpdatedAt > now) return null

    val elapsedMillis = now - lastUpdatedAt
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        return DateUtils.getRelativeTimeSpanString(
            context,
            lastUpdatedAt,
            false,
        ).toString()
    }

    val elapsedMinutes = (elapsedMillis / 60_000L).coerceAtLeast(1L)
    val (amount, unit) = when {
        elapsedMinutes < 60 -> elapsedMinutes to RelativeDateTimeFormatter.RelativeDateTimeUnit.MINUTE
        elapsedMinutes < 1_440 -> elapsedMinutes / 60 to RelativeDateTimeFormatter.RelativeDateTimeUnit.HOUR
        elapsedMinutes < 10_080 -> elapsedMinutes / 1_440 to RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY
        elapsedMinutes < 43_200 -> elapsedMinutes / 10_080 to RelativeDateTimeFormatter.RelativeDateTimeUnit.WEEK
        elapsedMinutes < 525_600 -> elapsedMinutes / 43_200 to RelativeDateTimeFormatter.RelativeDateTimeUnit.MONTH
        else -> elapsedMinutes / 525_600 to RelativeDateTimeFormatter.RelativeDateTimeUnit.YEAR
    }
    return RelativeDateTimeFormatter.getInstance(locale).formatNumeric(-amount.toDouble(), unit)
}
