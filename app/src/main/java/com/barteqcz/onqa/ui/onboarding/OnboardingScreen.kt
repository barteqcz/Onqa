package com.barteqcz.onqa.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import com.barteqcz.onqa.R

@Composable
fun OnboardingScreen(
    onGrantClick: () -> Unit,
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
                text = stringResource(R.string.welcome_title),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                textAlign = textAlign
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.welcome_desc),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = textAlign,
                lineHeight = 24.sp
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = alignment
            ) {
                PermissionItem(
                    icon = Icons.Rounded.LocationOn,
                    title = stringResource(R.string.permission_location_title),
                    desc = stringResource(R.string.permission_location_desc)
                )

                Spacer(modifier = Modifier.height(24.dp))

                PermissionItem(
                    icon = Icons.Rounded.Notifications,
                    title = stringResource(R.string.permission_notifications_title),
                    desc = stringResource(R.string.permission_notifications_desc)
                )
            }

            Button(
                onClick = onGrantClick,
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
                    text = stringResource(R.string.grant_permissions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(32.dp)
                .padding(top = 4.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start
            )
            Text(
                text = desc,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
                textAlign = TextAlign.Start
            )
        }
    }
}
