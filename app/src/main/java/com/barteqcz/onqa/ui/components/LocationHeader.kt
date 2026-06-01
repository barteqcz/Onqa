package com.barteqcz.onqa.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    if (info.city == null) {
        Spacer(modifier = Modifier.height(0.dp))
        return
    }

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
            targetState = info.city,
            transitionSpec = {
                fadeIn(tween(1000)).togetherWith(fadeOut(tween(1000)))
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
                
                AnimatedVisibility(
                    visible = info.distanceKm != null,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Text(
                        text = if (info.distanceKm == 0) stringResource(R.string.less_than_one_km_with_dot) else stringResource(R.string.distance_with_dot, info.distanceKm ?: 0),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
