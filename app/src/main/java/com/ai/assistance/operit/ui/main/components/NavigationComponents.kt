package com.ai.assistance.operit.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Displays a header in the navigation drawer */
@Composable
fun NavigationDrawerItemHeader(title: String, appearance: NavigationDrawerAppearance) {
    Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = appearance.titleColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp)
    )
}

/** Displays a navigation item in the drawer with icon and label */
@Composable
fun CompactNavigationDrawerItem(
        icon: ImageVector,
        label: String,
        selected: Boolean,
        appearance: NavigationDrawerAppearance,
        onClick: () -> Unit
) {
    val itemShape = MaterialTheme.shapes.extraSmall
    Surface(
            modifier =
                    Modifier.fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .height(48.dp),
            onClick = onClick,
            color = if (selected) appearance.selectedContainerColor else Color.Transparent,
            shape = itemShape
    ) {
        Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                    modifier =
                            Modifier.width(3.dp)
                                    .height(22.dp)
                                    .background(
                                            if (selected) appearance.selectedContentColor
                                            else Color.Transparent,
                                            MaterialTheme.shapes.extraSmall
                                    )
            )

            Spacer(modifier = Modifier.width(11.dp))

            Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint =
                            if (selected) appearance.selectedContentColor
                            else appearance.itemColor,
                    modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color =
                            if (selected) appearance.selectedContentColor
                            else appearance.itemColor
            )
        }
    }
}
