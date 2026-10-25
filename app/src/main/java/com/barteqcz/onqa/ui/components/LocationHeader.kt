package com.barteqcz.onqa.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
fun LocationHeader(info: LocationInfo) {
    val cityText = info.city ?: stringResource(R.string.unknown_location)

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
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun CompactLocationHeader(
    info: LocationInfo,
    modifier: Modifier = Modifier
) {
    val cityText = info.city ?: stringResource(R.string.unknown_location)
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
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
