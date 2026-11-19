package com.union.campus.ui.screens.life

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.union.campus.ui.prism.PrismCard

/** 生活入口：位置、实时读数和下一步分层，不把长名称挤进指标行。 */
@Composable
internal fun LifeServiceCard(
    title: String,
    location: String,
    value: String,
    detail: String,
    icon: ImageVector,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onContainer: Color = MaterialTheme.colorScheme.onSurface,
    status: String? = null,
    statusIcon: ImageVector = icon,
) {
    PrismCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = container,
        corner = 28f,
        contentPadding = PaddingValues(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = onContainer, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = onContainer)
                Text(location, style = MaterialTheme.typography.bodyMedium, color = onContainer)
            }
            if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = onContainer)
        }
        Spacer(Modifier.height(16.dp))
        Text(value, style = MaterialTheme.typography.headlineLarge, color = onContainer)
        Spacer(Modifier.height(4.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = onContainer)
        status?.let {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = onContainer.copy(alpha = 0.18f))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(statusIcon, null, tint = onContainer, modifier = Modifier.size(20.dp))
                Text(it, style = MaterialTheme.typography.labelLarge, color = onContainer)
            }
        }
    }
}
