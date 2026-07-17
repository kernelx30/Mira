package com.ai.assistance.operit.ui.main.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.MiraLogo
import com.ai.assistance.operit.ui.common.NavItem

private data class MateDestination(
    val item: NavItem,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
)

private val mateDestinations =
    listOf(
        MateDestination(NavItem.AiChat, R.string.mate_nav_companion, Icons.Filled.Forum, Icons.Outlined.Forum),
        MateDestination(
            NavItem.MemoryBase,
            R.string.mate_nav_memory,
            Icons.Filled.AutoStories,
            Icons.Outlined.AutoStories,
        ),
        MateDestination(NavItem.Settings, R.string.mate_nav_me, Icons.Filled.Person, Icons.Outlined.Person),
    )

@Composable
fun MateBottomNavigation(
    selectedItem: NavItem?,
    onSelect: (NavItem) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        mateDestinations.forEach { destination ->
            val selected = selectedItem == destination.item
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onSelect(destination.item) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.icon,
                        contentDescription = stringResource(destination.labelRes),
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
        }
    }
}

@Composable
fun MateNavigationRail(
    selectedItem: NavItem?,
    onSelect: (NavItem) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        header = {
            Surface(
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp).size(48.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primary,
            ) {
                MiraLogo(
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = stringResource(R.string.app_name),
                )
            }
        },
    ) {
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            mateDestinations.forEach { destination ->
                val selected = selectedItem == destination.item
                NavigationRailItem(
                    selected = selected,
                    onClick = { if (!selected) onSelect(destination.item) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.icon,
                            contentDescription = stringResource(destination.labelRes),
                        )
                    },
                    label = { Text(stringResource(destination.labelRes)) },
                    alwaysShowLabel = true,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onMore, modifier = Modifier.padding(bottom = 16.dp)) {
            Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.mate_nav_more))
        }
    }
}
