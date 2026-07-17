package com.ai.assistance.operit.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.MiraLogo
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.main.screens.ScreenRouteRegistry
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import com.ai.assistance.operit.ui.main.screens.Screen
import com.ai.assistance.operit.ui.theme.liquidGlass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Content for the expanded navigation drawer */
@Composable
fun DrawerContent(
        navItems: List<NavItem>,
        pluginEntries: List<NavigationEntrySpec>,
        selectedItem: NavItem?,
        selectedRouteId: String,
        isNetworkAvailable: Boolean,
        networkType: String,
        appearance: NavigationDrawerAppearance,
        topContentPadding: Dp? = null,
        scope: CoroutineScope,
        drawerState: androidx.compose.material3.DrawerState,
        onScreenSelected: (Screen) -> Unit,
        onNavigationEntrySelected: (NavigationEntrySpec) -> Unit
) {
        val context = LocalContext.current
        val drawerBrandName = context.getString(R.string.app_name)
        val bottomInset =
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val resolvedTopContentPadding =
                topContentPadding ?:
                WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val fixedBottomItems = remember {
                setOf(NavItem.Settings, NavItem.Help, NavItem.About)
        }
        val quickActionItems = remember {
                setOf(NavItem.AssistantConfig, NavItem.MemoryBase, NavItem.Toolbox)
        }
        val primaryNavItems =
                remember(navItems) {
                        navItems.filterNot {
                                it in fixedBottomItems || it in quickActionItems
                        }
                }
        val handleScreenSelection: (Screen) -> Unit = { screen ->
                val shouldCloseBeforeNavigate =
                        drawerState.currentValue == DrawerValue.Open ||
                                drawerState.targetValue == DrawerValue.Open

                scope.launch {
                        if (shouldCloseBeforeNavigate) {
                                drawerState.close()
                        }
                        onScreenSelected(screen)
                }
        }
        val handleNavItemClick: (NavItem) -> Unit = { item ->
                handleScreenSelection(ScreenRouteRegistry.defaultScreenForNavItem(item))
        }
        val handleNavigationEntryClick: (NavigationEntrySpec) -> Unit = { entry ->
                val shouldCloseBeforeNavigate =
                        drawerState.currentValue == DrawerValue.Open ||
                                drawerState.targetValue == DrawerValue.Open
                scope.launch {
                        if (shouldCloseBeforeNavigate) {
                                drawerState.close()
                        }
                        onNavigationEntrySelected(entry)
                }
        }

        Column(
                modifier =
                        Modifier.fillMaxHeight()
                                .padding(
                                        top = resolvedTopContentPadding,
                                        end = 8.dp,
                                        bottom = bottomInset
                                )
        ) {
                Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                ) {
                        NewSidebarTopContent(
                                selectedItem = selectedItem,
                                pluginEntries = pluginEntries,
                                selectedRouteId = selectedRouteId,
                                brandName = drawerBrandName,
                                isNetworkAvailable = isNetworkAvailable,
                                networkType = networkType,
                                appearance = appearance,
                                navItems = primaryNavItems,
                                onNavItemClick = handleNavItemClick,
                                onNavigationEntryClick = handleNavigationEntryClick
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                }

                HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        thickness = 0.5.dp,
                        color = appearance.dividerColor.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))

                DrawerBottomShortcutRow(
                        selectedItem = selectedItem,
                        appearance = appearance,
                        onNavItemClick = handleNavItemClick
                )
        }
}

/** Content for the collapsed navigation drawer (for tablet mode) */
@Composable
fun CollapsedDrawerContent(
        navItems: List<NavItem>,
        pluginEntries: List<NavigationEntrySpec>,
        selectedItem: NavItem?,
        selectedRouteId: String,
        isNetworkAvailable: Boolean,
        appearance: NavigationDrawerAppearance,
        onScreenSelected: (Screen) -> Unit,
        onNavigationEntrySelected: (NavigationEntrySpec) -> Unit
) {
        Column(
                modifier =
                        Modifier.fillMaxHeight()
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                        modifier =
                                Modifier.size(48.dp)
                                        .liquidGlass(
                                                enabled = appearance.buttonLiquidGlassEnabled,
                                                shape = CircleShape,
                                                containerColor = appearance.buttonContainerColor,
                                                shadowElevation = 5.dp,
                                                borderWidth = 0.5.dp,
                                                blurRadius = 14.dp,
                                                overlayAlphaBoost = 0.05f,
                                                enableLens = false
                                        )
                                        .clip(CircleShape),
                        color = Color.Transparent,
                        shape = CircleShape
                ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                        imageVector =
                                                if (isNetworkAvailable) Icons.Default.Wifi
                                                else Icons.Default.WifiOff,
                                        contentDescription = stringResource(id = R.string.network_status_label),
                                        tint = appearance.statusAvailableColor,
                                        modifier = Modifier.size(24.dp)
                                )
                        }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(0.6f),
                        color = appearance.dividerColor
                )
                Spacer(modifier = Modifier.height(16.dp))

                for (item in navItems) {
                        val selectedGlassOverlayColor =
                                if (selectedItem == item) {
                                        appearance.selectedContainerColor.copy(alpha = 0.18f)
                                } else {
                                        Color.Transparent
                                }
                        Surface(
                                modifier =
                                        Modifier.padding(vertical = 8.dp)
                                                .size(48.dp)
                                                .liquidGlass(
                                                        enabled = appearance.buttonLiquidGlassEnabled,
                                                        shape = CircleShape,
                                                        containerColor =
                                                                appearance.buttonContainerColor,
                                                        shadowElevation =
                                                                if (selectedItem == item) 6.dp else 5.dp,
                                                        borderWidth = 0.5.dp,
                                                        blurRadius = 14.dp,
                                                        overlayAlphaBoost = 0.05f,
                                                        enableLens = false
                                                )
                                                .clip(CircleShape)
                                                .background(selectedGlassOverlayColor),
                                color = Color.Transparent,
                                shape = CircleShape
                        ) {
                                IconButton(
                                        onClick = {
                                                onScreenSelected(
                                                        ScreenRouteRegistry.defaultScreenForNavItem(item)
                                                )
                                        }
                                ) {
                                        Icon(
                                                imageVector = item.icon,
                                                contentDescription = stringResource(id = item.titleResId),
                                                tint =
                                                        if (selectedItem == item) {
                                                                if (appearance.buttonLiquidGlassEnabled) {
                                                                        appearance.selectedContentColor
                                                                } else {
                                                                        appearance.titleColor
                                                                }
                                                        } else {
                                                                appearance.itemColor
                                                        },
                                                modifier = Modifier.size(24.dp)
                                        )
                                }
                        }
                }

                if (pluginEntries.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(
                                modifier = Modifier.fillMaxWidth(0.45f),
                                color = appearance.dividerColor
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        pluginEntries.forEach { entry ->
                                val selectedGlassOverlayColor =
                                        if (selectedRouteId == entry.routeId) {
                                                appearance.selectedContainerColor.copy(alpha = 0.18f)
                                        } else {
                                                Color.Transparent
                                        }
                                Surface(
                                        modifier =
                                                Modifier.padding(vertical = 8.dp)
                                                        .size(48.dp)
                                                        .liquidGlass(
                                                                enabled = appearance.buttonLiquidGlassEnabled,
                                                                shape = CircleShape,
                                                                containerColor = appearance.buttonContainerColor,
                                                                shadowElevation =
                                                                        if (selectedRouteId == entry.routeId) 6.dp else 5.dp,
                                                                borderWidth = 0.5.dp,
                                                                blurRadius = 14.dp,
                                                                overlayAlphaBoost = 0.05f,
                                                                enableLens = false
                                                        )
                                                        .clip(CircleShape)
                                                        .background(selectedGlassOverlayColor),
                                        color = Color.Transparent,
                                        shape = CircleShape
                                ) {
                                        IconButton(onClick = { onNavigationEntrySelected(entry) }) {
                                                Icon(
                                                        imageVector = entry.icon,
                                                        contentDescription = entry.title,
                                                        tint =
                                                                if (selectedRouteId == entry.routeId) {
                                                                        if (appearance.buttonLiquidGlassEnabled) {
                                                                                appearance.selectedContentColor
                                                                        } else {
                                                                                appearance.titleColor
                                                                        }
                                                                } else {
                                                                        appearance.itemColor
                                                                },
                                                        modifier = Modifier.size(24.dp)
                                                )
                                        }
                                }
                        }
                }

                Spacer(modifier = Modifier.height(16.dp))
        }
}

@Composable
private fun NewSidebarTopContent(
        selectedItem: NavItem?,
        pluginEntries: List<NavigationEntrySpec>,
        selectedRouteId: String,
        brandName: String,
        isNetworkAvailable: Boolean,
        networkType: String,
        appearance: NavigationDrawerAppearance,
        navItems: List<NavItem>,
        onNavItemClick: (NavItem) -> Unit,
        onNavigationEntryClick: (NavigationEntrySpec) -> Unit
) {
        Spacer(modifier = Modifier.height(16.dp))

        SidebarInfoCard(
                brandName = brandName,
                isNetworkAvailable = isNetworkAvailable,
                networkType = networkType,
                appearance = appearance
        )

        Spacer(modifier = Modifier.height(18.dp))
        SidebarSectionTitle(
                text = stringResource(R.string.mate_drawer_relationship),
                appearance = appearance
        )
        listOf(NavItem.AiChat, NavItem.AssistantConfig, NavItem.MemoryBase).forEach { item ->
                CompactNavigationDrawerItem(
                        icon = item.icon,
                        label = stringResource(id = item.titleResId),
                        selected = selectedItem == item,
                        appearance = appearance,
                        onClick = { onNavItemClick(item) }
                )
        }

        Spacer(modifier = Modifier.height(14.dp))
        SidebarSectionTitle(
                text = stringResource(R.string.mate_drawer_device_extensions),
                appearance = appearance
        )
        (listOf(NavItem.Toolbox) + navItems.filterNot { it == NavItem.AiChat }).forEach { item ->
                CompactNavigationDrawerItem(
                        icon = item.icon,
                        label = stringResource(id = item.titleResId),
                        selected = selectedItem == item,
                        appearance = appearance,
                        onClick = { onNavItemClick(item) }
                )
        }

        if (pluginEntries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                SidebarSectionTitle(
                        text = stringResource(id = R.string.nav_group_plugins),
                        appearance = appearance
                )
                pluginEntries.forEach { entry ->
                        CompactNavigationDrawerItem(
                                icon = entry.icon,
                                label = entry.title,
                                selected = selectedRouteId == entry.routeId,
                                appearance = appearance,
                                onClick = { onNavigationEntryClick(entry) }
                        )
                }
        }
}

@Composable
private fun SidebarSectionTitle(
        text: String,
        appearance: NavigationDrawerAppearance
) {
        Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = appearance.titleColor.copy(alpha = 0.68f),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 28.dp, end = 20.dp, bottom = 6.dp)
        )
}

@Composable
private fun SidebarInfoCard(
        brandName: String,
        isNetworkAvailable: Boolean,
        networkType: String,
        appearance: NavigationDrawerAppearance
) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
                Surface(
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary
                ) {
                        MiraLogo(modifier = Modifier.fillMaxSize())
                }
                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = brandName,
                                style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 0.sp),
                                color = appearance.titleColor,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                        modifier =
                                                Modifier
                                                        .size(7.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                                if (isNetworkAvailable) Color(0xFF3F8F6B)
                                                                else MaterialTheme.colorScheme.error
                                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                        text = networkType,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = appearance.itemColor.copy(alpha = 0.76f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                        }
                }
        }
}

@Composable
private fun SidebarQuickActionCard(
        modifier: Modifier = Modifier,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        label: String,
        badgeText: String? = null,
        selected: Boolean,
        appearance: NavigationDrawerAppearance,
        onClick: () -> Unit
) {
        val itemShape = RoundedCornerShape(12.dp)
        val selectedGlassOverlayColor =
                if (selected) {
                        appearance.selectedContainerColor.copy(alpha = 0.18f)
                } else {
                        Color.Transparent
                }
        val accentColor = appearance.selectedContentColor
        Surface(
                modifier =
                        modifier.height(76.dp)
                                .liquidGlass(
                                        enabled = appearance.buttonLiquidGlassEnabled,
                                        shape = itemShape,
                                        containerColor = appearance.buttonContainerColor,
                                        shadowElevation = if (selected) 4.dp else 2.dp,
                                        borderWidth = 0.5.dp,
                                        blurRadius = 16.dp,
                                        overlayAlphaBoost = 0.04f,
                                        enableLens = false
                                )
                                .clip(itemShape)
                                .background(selectedGlassOverlayColor),
                onClick = onClick,
                color =
                        if (appearance.buttonLiquidGlassEnabled) {
                                Color.Transparent
                        } else if (selected) {
                                appearance.selectedContainerColor
                        } else {
                                Color.Transparent
                        },
                shape = itemShape
        ) {
                Box(modifier = Modifier.fillMaxWidth().height(76.dp)) {
                        if (selected) {
                                Box(
                                        modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth(0.5f)
                                                .height(2.dp)
                                                .padding(bottom = 4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(accentColor.copy(alpha = 0.7f))
                                )
                        }
                        if (!badgeText.isNullOrBlank()) {
                                SidebarQuickActionBadge(
                                        text = badgeText,
                                        appearance = appearance,
                                        selected = selected,
                                        modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(top = 6.dp, end = 6.dp)
                                )
                        }
                        Column(
                                modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                        ) {
                                Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint =
                                                if (selected) appearance.selectedContentColor
                                                else appearance.itemColor,
                                        modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                                letterSpacing = 0.sp
                                        ),
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color =
                                                if (selected) appearance.selectedContentColor
                                                else appearance.itemColor,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                        }
                }
        }
}

@Composable
private fun SidebarQuickActionBadge(
        text: String,
        appearance: NavigationDrawerAppearance,
        selected: Boolean,
        modifier: Modifier = Modifier
) {
        Surface(
                modifier = modifier,
                shape = RoundedCornerShape(50),
                color =
                        if (selected) {
                                appearance.selectedContentColor.copy(alpha = 0.16f)
                        } else {
                                appearance.selectedContainerColor.copy(alpha = 0.18f)
                        }
        ) {
                Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 0.sp
                        ),
                        color =
                                if (selected) appearance.selectedContentColor
                                else appearance.titleColor.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                )
        }
}

@Composable
private fun DrawerBottomShortcutRow(
        selectedItem: NavItem?,
        appearance: NavigationDrawerAppearance,
        onNavItemClick: (NavItem) -> Unit
) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
        ) {
                BottomShortcutDrawerItem(
                        item = NavItem.About,
                        selected = selectedItem == NavItem.About,
                        appearance = appearance,
                        onClick = { onNavItemClick(NavItem.About) }
                )
                BottomShortcutDrawerItem(
                        item = NavItem.Help,
                        selected = selectedItem == NavItem.Help,
                        appearance = appearance,
                        onClick = { onNavItemClick(NavItem.Help) }
                )
                BottomShortcutDrawerItem(
                        item = NavItem.Settings,
                        selected = selectedItem == NavItem.Settings,
                        appearance = appearance,
                        onClick = { onNavItemClick(NavItem.Settings) }
                )
        }
}

@Composable
private fun BottomShortcutDrawerItem(
        modifier: Modifier = Modifier,
        item: NavItem,
        selected: Boolean,
        appearance: NavigationDrawerAppearance,
        onClick: () -> Unit
) {
        Surface(
                modifier = modifier.size(48.dp),
                onClick = onClick,
                color = if (selected) appearance.selectedContainerColor else Color.Transparent,
                shape = CircleShape
        ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                                imageVector = item.icon,
                                contentDescription = stringResource(id = item.titleResId),
                                tint =
                                        if (selected) appearance.selectedContentColor
                                        else appearance.itemColor,
                                modifier = Modifier.size(21.dp)
                        )
                }
        }
}
