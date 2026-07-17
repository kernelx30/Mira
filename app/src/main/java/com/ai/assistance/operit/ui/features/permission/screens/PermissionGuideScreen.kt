package com.ai.assistance.operit.ui.features.permission.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.ai.assistance.operit.util.AppLogger
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.MiraLogo
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.ui.features.permission.viewmodel.PermissionGuideViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val INTRO_PAGES_COUNT = 3
private const val BASIC_PERMISSIONS_PAGE_INDEX = INTRO_PAGES_COUNT
private const val PERMISSION_LEVEL_PAGE_INDEX = INTRO_PAGES_COUNT + 1
private const val TOTAL_PAGES_COUNT = INTRO_PAGES_COUNT + 2

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PermissionGuideScreen(
        viewModel: PermissionGuideViewModel = viewModel(),
        onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 状态
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(pageCount = { TOTAL_PAGES_COUNT })

    // 警告对话框状态
    var showPermissionWarning by remember { mutableStateOf(false) }

    // 初始化
    LaunchedEffect(Unit) { viewModel.checkPermissions(context) }

    // 存储权限请求启动器 (适用于Android 10及以下版本)
    val storagePermissionLauncher =
            rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    val readGranted =
                            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
                    val writeGranted =
                            permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
                    if (readGranted && writeGranted) {
                        // 使用已存在的方法检查权限状态，而不是直接更新
                        viewModel.checkPermissions(context)
                    }
                }
            }

    // 位置权限请求启动器
    val locationPermissionLauncher =
            rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
                if (fineGranted || coarseGranted) {
                    viewModel.updateLocationPermission(true)
                }
            }

    // 页面切换效果
    LaunchedEffect(pagerState.currentPage) {
        when (pagerState.currentPage) {
            in 0 until INTRO_PAGES_COUNT ->
                viewModel.setCurrentStep(PermissionGuideViewModel.Step.WELCOME)
            BASIC_PERMISSIONS_PAGE_INDEX ->
                viewModel.setCurrentStep(PermissionGuideViewModel.Step.BASIC_PERMISSIONS)
            PERMISSION_LEVEL_PAGE_INDEX ->
                viewModel.setCurrentStep(PermissionGuideViewModel.Step.PERMISSION_LEVEL)
        }
    }

    // 完成设置后的回调
    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) {
            delay(500) // 短暂延迟，让用户看到完成状态
            onComplete()
        }
    }

    // 权限警告对话框
    if (showPermissionWarning) {
        AlertDialog(
                onDismissRequest = { showPermissionWarning = false },
                icon = {
                    Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    Text(
                            text = stringResource(R.string.permission_guide_warning_title),
                            style = MaterialTheme.typography.titleMedium
                    )
                },
                text = {
                    Text(
                            text = stringResource(R.string.permission_guide_warning_message),
                            style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                            onClick = {
                                showPermissionWarning = false
                                scope.launch {
                                    pagerState.animateScrollToPage(PERMISSION_LEVEL_PAGE_INDEX)
                                }
                            }
                    ) { Text(stringResource(R.string.permission_guide_warning_continue)) }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionWarning = false }) {
                        Text(stringResource(R.string.permission_guide_warning_return))
                    }
                }
        )
    }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PermissionGuideTopBar(
                currentPage = pagerState.currentPage,
                totalPages = pagerState.pageCount
        )

        // 进度指示器
        LinearProgressIndicator(
                progress = { (pagerState.currentPage + 1).toFloat() / pagerState.pageCount },
                modifier =
                        Modifier.fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        // 主内容
        HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                userScrollEnabled = false
        ) { page ->
            when (page) {
                0 ->
                        IntroductionPage(
                                title = stringResource(R.string.permission_guide_intro_1_title),
                                description =
                                        stringResource(R.string.permission_guide_intro_1_desc),
                                pageIndex = 0
                        )
                1 ->
                        IntroductionPage(
                                title = stringResource(R.string.permission_guide_intro_2_title),
                                description =
                                        stringResource(R.string.permission_guide_intro_2_desc),
                                pageIndex = 1
                        )
                2 ->
                        IntroductionPage(
                                title = stringResource(R.string.permission_guide_intro_3_title),
                                description =
                                        stringResource(R.string.permission_guide_intro_3_desc),
                                pageIndex = 2
                        )
                BASIC_PERMISSIONS_PAGE_INDEX ->
                        MateBasicPermissionsPage(
                                hasStoragePermission = uiState.hasStoragePermission,
                                hasOverlayPermission = uiState.hasOverlayPermission,
                                hasBatteryOptimizationExemption =
                                        uiState.hasBatteryOptimizationExemption,
                                hasLocationPermission = uiState.hasLocationPermission,
                                onStoragePermissionClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        // Android 11+: 使用更精确的ALL_FILES_ACCESS权限页面
                                        try {
                                            val intent =
                                                    Intent(
                                                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                                                            )
                                                            .apply {
                                                                data =
                                                                        Uri.parse(
                                                                                "package:${context.packageName}"
                                                                        )
                                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            AppLogger.e("PermissionGuide", "无法直接打开应用存储权限页面", e)
                                            // 回退到通用设置页面
                                            try {
                                                val intent =
                                                        Intent(
                                                                Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                                                        )
                                                context.startActivity(intent)
                                            } catch (e2: Exception) {
                                                Toast.makeText(
                                                                context,
                                                                context.getString(
                                                                        R.string
                                                                                .permission_guide_storage_setting_failed
                                                                ),
                                                                Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                            }
                                        }
                                    } else {
                                        // Android 10及以下: 使用标准权限请求API
                                        storagePermissionLauncher.launch(
                                                arrayOf(
                                                        Manifest.permission.READ_EXTERNAL_STORAGE,
                                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                                )
                                        )
                                    }
                                },
                                onOverlayPermissionClick = {
                                    try {
                                        // 直接使用包名跳转到悬浮窗权限页面
                                        val intent =
                                                Intent(
                                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                        Uri.parse("package:" + context.packageName)
                                                )
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                                        context,
                                                        context.getString(
                                                                R.string
                                                                        .permission_guide_overlay_setting_failed
                                                        ),
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                    }
                                },
                                onBatteryOptimizationClick = {
                                    try {
                                        // 直接请求忽略电池优化，无需用户搜索应用
                                        val intent =
                                                Intent(
                                                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                                        )
                                                        .apply {
                                                            data =
                                                                    Uri.parse(
                                                                            "package:" +
                                                                                    context.packageName
                                                                    )
                                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // 如果直接请求失败，尝试打开电池优化设置页面
                                        try {
                                            val intent =
                                                    Intent(
                                                            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                                                    )
                                            context.startActivity(intent)
                                            Toast.makeText(
                                                            context,
                                                            context.getString(
                                                                    R.string
                                                                            .permission_guide_battery_hint
                                                            ),
                                                            Toast.LENGTH_LONG
                                                    )
                                                    .show()
                                        } catch (e2: Exception) {
                                            Toast.makeText(
                                                            context,
                                                            context.getString(
                                                                    R.string
                                                                            .permission_guide_battery_setting_failed
                                                            ),
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                        }
                                    }
                                },
                                onLocationPermissionClick = {
                                    // 直接请求位置权限
                                    locationPermissionLauncher.launch(
                                            arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                    )
                                },
                                onRefresh = { viewModel.checkPermissions(context) }
                        )
                PERMISSION_LEVEL_PAGE_INDEX ->
                        MatePermissionLevelPage(
                                selectedLevel = uiState.selectedPermissionLevel,
                                onLevelSelected = { level ->
                                    viewModel.selectPermissionLevel(level)
                                }
                        )
            }
        }

        PermissionGuideNavigationBar(
                currentPage = pagerState.currentPage,
                canGoBack = pagerState.currentPage > 0,
                canGoForward =
                        pagerState.currentPage != PERMISSION_LEVEL_PAGE_INDEX ||
                                uiState.selectedPermissionLevel != null,
                onBack = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                },
                onForward = {
                    scope.launch {
                        when {
                            pagerState.currentPage == PERMISSION_LEVEL_PAGE_INDEX &&
                                    uiState.selectedPermissionLevel != null -> {
                                viewModel.savePermissionLevel()
                            }
                            pagerState.currentPage == BASIC_PERMISSIONS_PAGE_INDEX &&
                                    !uiState.allBasicPermissionsGranted -> {
                                showPermissionWarning = true
                            }
                            pagerState.currentPage < pagerState.pageCount - 1 -> {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    }
                }
        )
    }
}

@Composable
private fun PermissionGuideTopBar(currentPage: Int, totalPages: Int) {
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
        ) {
            MiraLogo(modifier = Modifier.fillMaxSize())
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
            )
            Text(
                    text = stringResource(R.string.mate_onboarding_setup_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
                text = "${currentPage + 1} / $totalPages",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionGuideNavigationBar(
        currentPage: Int,
        canGoBack: Boolean,
        canGoForward: Boolean,
        onBack: () -> Unit,
        onForward: () -> Unit
) {
    val stepLabel =
            when (currentPage) {
                in 0 until INTRO_PAGES_COUNT ->
                        stringResource(
                                R.string.permission_guide_intro_page_indicator,
                                currentPage + 1,
                                INTRO_PAGES_COUNT
                        )
                BASIC_PERMISSIONS_PAGE_INDEX ->
                        stringResource(R.string.permission_guide_basic_permissions)
                PERMISSION_LEVEL_PAGE_INDEX ->
                        stringResource(R.string.permission_guide_permission_level)
                else -> ""
            }
    val isLastPage = currentPage == PERMISSION_LEVEL_PAGE_INDEX

    Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp
    ) {
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .widthIn(max = 1180.dp)
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                    onClick = onBack,
                    enabled = canGoBack,
                    modifier = Modifier.widthIn(min = 112.dp),
                    shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.permission_guide_previous))
            }
            Text(
                    text = stepLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
            )
            Button(
                    onClick = onForward,
                    enabled = canGoForward,
                    modifier = Modifier.widthIn(min = 112.dp),
                    shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                        if (isLastPage) {
                            stringResource(R.string.permission_guide_complete)
                        } else {
                            stringResource(R.string.permission_guide_next)
                        }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                        imageVector = if (isLastPage) Icons.Default.Check else Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun IntroductionPage(title: String, description: String, pageIndex: Int) {
    val icon =
            when (pageIndex) {
                0 -> Icons.Default.Favorite
                1 -> Icons.Default.History
                else -> Icons.Default.Build
            }
    val sectionLabel =
            stringResource(
                    when (pageIndex) {
                        0 -> R.string.mate_onboarding_companion_label
                        1 -> R.string.mate_onboarding_memory_label
                        else -> R.string.mate_onboarding_capability_label
                    }
            )
    val detailIds =
            when (pageIndex) {
                0 ->
                        listOf(
                                R.string.mate_onboarding_companion_point_1,
                                R.string.mate_onboarding_companion_point_2,
                                R.string.mate_onboarding_companion_point_3
                        )
                1 ->
                        listOf(
                                R.string.mate_onboarding_memory_point_1,
                                R.string.mate_onboarding_memory_point_2,
                                R.string.mate_onboarding_memory_point_3
                        )
                else ->
                        listOf(
                                R.string.mate_onboarding_capability_point_1,
                                R.string.mate_onboarding_capability_point_2,
                                R.string.mate_onboarding_capability_point_3
                        )
            }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wideLayout = maxWidth >= 760.dp
        val contentModifier =
                Modifier.fillMaxSize()
                        .widthIn(max = 1040.dp)
                        .align(Alignment.Center)
                        .padding(horizontal = 28.dp, vertical = 24.dp)
        if (wideLayout) {
            Row(
                    modifier = contentModifier,
                    horizontalArrangement = Arrangement.spacedBy(56.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                IntroductionSignal(
                        icon = icon,
                        pageIndex = pageIndex,
                        sectionLabel = sectionLabel,
                        modifier = Modifier.width(300.dp)
                )
                IntroductionCopy(
                        title = title,
                        description = description,
                        detailIds = detailIds,
                        modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(
                    modifier = contentModifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                    horizontalAlignment = Alignment.Start
            ) {
                IntroductionSignal(
                        icon = icon,
                        pageIndex = pageIndex,
                        sectionLabel = sectionLabel,
                        modifier = Modifier.fillMaxWidth()
                )
                IntroductionCopy(
                        title = title,
                        description = description,
                        detailIds = detailIds
                )
            }
        }
    }
}

@Composable
private fun IntroductionSignal(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        pageIndex: Int,
        sectionLabel: String,
        modifier: Modifier = Modifier
) {
    val containerColor =
            when (pageIndex) {
                0 -> MaterialTheme.colorScheme.primaryContainer
                1 -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.tertiaryContainer
            }
    val contentColor =
            when (pageIndex) {
                0 -> MaterialTheme.colorScheme.onPrimaryContainer
                1 -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onTertiaryContainer
            }
    Surface(
            modifier = modifier.height(220.dp),
            shape = RoundedCornerShape(8.dp),
            color = containerColor
    ) {
        Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(44.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                        text = "0${pageIndex + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor.copy(alpha = 0.72f)
                )
                Text(
                        text = sectionLabel,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                )
            }
        }
    }
}

@Composable
private fun IntroductionCopy(
        title: String,
        description: String,
        detailIds: List<Int>,
        modifier: Modifier = Modifier
) {
    Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
        )
        Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        detailIds.forEach { detailId ->
            Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
            ) {
                Box(
                        modifier =
                                Modifier.padding(top = 7.dp)
                                        .size(7.dp)
                                        .background(
                                                MaterialTheme.colorScheme.primary,
                                                CircleShape
                                        )
                )
                Text(
                        text = stringResource(detailId),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MateBasicPermissionsPage(
        hasStoragePermission: Boolean,
        hasOverlayPermission: Boolean,
        hasBatteryOptimizationExemption: Boolean,
        hasLocationPermission: Boolean,
        onStoragePermissionClick: () -> Unit,
        onOverlayPermissionClick: () -> Unit,
        onBatteryOptimizationClick: () -> Unit,
        onLocationPermissionClick: () -> Unit,
        onRefresh: () -> Unit
) {
    var refreshRotation by remember { mutableStateOf(0f) }
    val rotationAngle by
            animateFloatAsState(
                    targetValue = refreshRotation,
                    animationSpec = tween(500),
                    label = "Mate permission refresh"
            )
    val allGranted =
            hasStoragePermission &&
                    hasOverlayPermission &&
                    hasBatteryOptimizationExemption &&
                    hasLocationPermission

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wideLayout = maxWidth >= 760.dp
        Column(
                modifier =
                        Modifier.widthIn(max = 1040.dp)
                                .fillMaxSize()
                                .align(Alignment.Center)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 28.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            text = stringResource(R.string.permission_guide_basic_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                            text = stringResource(R.string.mate_permission_optional_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                        onClick = {
                            refreshRotation += 360f
                            onRefresh()
                        },
                        shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier =
                                    Modifier.size(17.dp).graphicsLayer {
                                        rotationZ = rotationAngle
                                    }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.permission_guide_check_permissions))
                }
            }

            val firstColumn: @Composable ColumnScope.() -> Unit = {
                MatePermissionItem(
                        title = stringResource(R.string.permission_guide_storage_title),
                        description = stringResource(R.string.permission_guide_storage_desc),
                        isGranted = hasStoragePermission,
                        onClick = onStoragePermissionClick
                )
                MatePermissionItem(
                        title = stringResource(R.string.permission_guide_overlay_title),
                        description = stringResource(R.string.permission_guide_overlay_desc),
                        isGranted = hasOverlayPermission,
                        onClick = onOverlayPermissionClick
                )
            }
            val secondColumn: @Composable ColumnScope.() -> Unit = {
                MatePermissionItem(
                        title = stringResource(R.string.permission_guide_battery_title),
                        description = stringResource(R.string.permission_guide_battery_desc),
                        isGranted = hasBatteryOptimizationExemption,
                        onClick = onBatteryOptimizationClick
                )
                MatePermissionItem(
                        title = stringResource(R.string.permission_guide_location_title),
                        description = stringResource(R.string.permission_guide_location_desc),
                        isGranted = hasLocationPermission,
                        onClick = onLocationPermissionClick
                )
            }

            if (wideLayout) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                ) {
                    Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            content = firstColumn
                    )
                    Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            content = secondColumn
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    firstColumn()
                    secondColumn()
                }
            }

            AnimatedVisibility(visible = allGranted, enter = fadeIn(), exit = fadeOut()) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .background(
                                                MaterialTheme.colorScheme.secondaryContainer,
                                                RoundedCornerShape(8.dp)
                                        )
                                        .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                    )
                    Text(
                            text = stringResource(R.string.permission_guide_all_granted),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun MatePermissionItem(
        title: String,
        description: String,
        isGranted: Boolean,
        onClick: () -> Unit
) {
    Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            shape = RoundedCornerShape(8.dp),
            color =
                    if (isGranted) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    }
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color =
                            if (isGranted) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                            imageVector = if (isGranted) Icons.Default.Check else Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint =
                                    if (isGranted) MaterialTheme.colorScheme.onSecondary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                    )
                }
            }
            Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                )
                Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MatePermissionLevelPage(
        selectedLevel: AndroidPermissionLevel?,
        onLevelSelected: (AndroidPermissionLevel) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wideLayout = maxWidth >= 760.dp
        Column(
                modifier =
                        Modifier.widthIn(max = 1040.dp)
                                .fillMaxSize()
                                .align(Alignment.Center)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 28.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                        text = stringResource(R.string.permission_guide_level_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                )
                Text(
                        text = stringResource(R.string.mate_permission_level_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val levels =
                    listOf(
                            Triple(
                                    AndroidPermissionLevel.STANDARD,
                                    stringResource(R.string.permission_guide_standard_title),
                                    stringResource(R.string.permission_guide_standard_desc)
                            ),
                            Triple(
                                    AndroidPermissionLevel.ACCESSIBILITY,
                                    stringResource(R.string.permission_guide_accessibility_title),
                                    stringResource(R.string.permission_guide_accessibility_desc)
                            ),
                            Triple(
                                    AndroidPermissionLevel.DEBUGGER,
                                    stringResource(R.string.permission_guide_debugger_title),
                                    stringResource(R.string.permission_guide_debugger_desc)
                            ),
                            Triple(
                                    AndroidPermissionLevel.ROOT,
                                    stringResource(R.string.permission_guide_root_title),
                                    stringResource(R.string.permission_guide_root_desc)
                            )
                    )

            if (wideLayout) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    levels.chunked(2).forEach { rowLevels ->
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                        ) {
                            rowLevels.forEach { (level, title, description) ->
                                MatePermissionLevelItem(
                                        level = level,
                                        title = title,
                                        description = description,
                                        isSelected = selectedLevel == level,
                                        onClick = { onLevelSelected(level) },
                                        modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    levels.forEach { (level, title, description) ->
                        MatePermissionLevelItem(
                                level = level,
                                title = title,
                                description = description,
                                isSelected = selectedLevel == level,
                                onClick = { onLevelSelected(level) }
                        )
                    }
                }
            }

            Text(
                    text = stringResource(R.string.permission_guide_change_anytime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MatePermissionLevelItem(
        level: AndroidPermissionLevel,
        title: String,
        description: String,
        isSelected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
) {
    Surface(
            modifier =
                    modifier.fillMaxWidth()
                            .heightIn(min = 112.dp)
                            .clickable(onClick = onClick),
            shape = RoundedCornerShape(8.dp),
            color =
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow,
            border =
                    if (isSelected) {
                        androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary
                        )
                    } else {
                        null
                    }
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
        ) {
            Surface(
                    modifier = Modifier.size(34.dp),
                    shape = RoundedCornerShape(8.dp),
                    color =
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                            imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Security,
                            contentDescription = null,
                            tint =
                                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color =
                                if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                )
                Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                )
                Text(
                        text = level.name,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun BasicPermissionsPage(
        hasStoragePermission: Boolean,
        hasOverlayPermission: Boolean,
        hasBatteryOptimizationExemption: Boolean,
        hasLocationPermission: Boolean,
        onStoragePermissionClick: () -> Unit,
        onOverlayPermissionClick: () -> Unit,
        onBatteryOptimizationClick: () -> Unit,
        onLocationPermissionClick: () -> Unit,
        onRefresh: () -> Unit
) {
    var refreshRotation by remember { mutableStateOf(0f) }
    val rotationAngle by
            animateFloatAsState(
                    targetValue = refreshRotation,
                    animationSpec = tween(500),
                    label = "Refresh Rotation"
            )

    val allGranted =
            hasStoragePermission &&
                    hasOverlayPermission &&
                    hasBatteryOptimizationExemption &&
                    hasLocationPermission

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wideLayout = maxWidth >= 760.dp
        Column(
                modifier =
                        Modifier.widthIn(max = 1040.dp)
                                .fillMaxSize()
                                .align(Alignment.Center)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 28.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            text = stringResource(R.string.permission_guide_basic_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                            text = stringResource(R.string.mate_permission_optional_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                        onClick = {
                            refreshRotation += 360f
                            onRefresh()
                        },
                        shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier =
                                    Modifier.size(17.dp).graphicsLayer {
                                        rotationZ = rotationAngle
                                    }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.permission_guide_check_permissions))
                }
            }

            if (wideLayout) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                ) {
                    Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PermissionItem(
                                title = stringResource(R.string.permission_guide_storage_title),
                                description = stringResource(R.string.permission_guide_storage_desc),
                                isGranted = hasStoragePermission,
                                onClick = onStoragePermissionClick
                        )
                        PermissionItem(
                                title = stringResource(R.string.permission_guide_overlay_title),
                                description = stringResource(R.string.permission_guide_overlay_desc),
                                isGranted = hasOverlayPermission,
                                onClick = onOverlayPermissionClick
                        )
                    }
                    Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PermissionItem(
                                title = stringResource(R.string.permission_guide_battery_title),
                                description = stringResource(R.string.permission_guide_battery_desc),
                                isGranted = hasBatteryOptimizationExemption,
                                onClick = onBatteryOptimizationClick
                        )
                        PermissionItem(
                                title = stringResource(R.string.permission_guide_location_title),
                                description = stringResource(R.string.permission_guide_location_desc),
                                isGranted = hasLocationPermission,
                                onClick = onLocationPermissionClick
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PermissionItem(
                            title = stringResource(R.string.permission_guide_storage_title),
                            description = stringResource(R.string.permission_guide_storage_desc),
                            isGranted = hasStoragePermission,
                            onClick = onStoragePermissionClick
                    )
                    PermissionItem(
                            title = stringResource(R.string.permission_guide_overlay_title),
                            description = stringResource(R.string.permission_guide_overlay_desc),
                            isGranted = hasOverlayPermission,
                            onClick = onOverlayPermissionClick
                    )
                    PermissionItem(
                            title = stringResource(R.string.permission_guide_battery_title),
                            description = stringResource(R.string.permission_guide_battery_desc),
                            isGranted = hasBatteryOptimizationExemption,
                            onClick = onBatteryOptimizationClick
                    )
                    PermissionItem(
                            title = stringResource(R.string.permission_guide_location_title),
                            description = stringResource(R.string.permission_guide_location_desc),
                            isGranted = hasLocationPermission,
                            onClick = onLocationPermissionClick
                    )
                }
            }

            AnimatedVisibility(visible = allGranted, enter = fadeIn(), exit = fadeOut()) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier =
                                Modifier.fillMaxWidth()
                                        .background(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(14.dp)
                ) {
                    Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                    )
                    Text(
                            text = stringResource(R.string.permission_guide_all_granted),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionItem(
        title: String,
        description: String,
        isGranted: Boolean,
        onClick: () -> Unit
) {
    Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            shape = RoundedCornerShape(8.dp),
            color =
                    if (isGranted) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    }
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                    modifier =
                            Modifier.size(32.dp)
                                    .background(
                                            color =
                                                    if (isGranted) {
                                                        MaterialTheme.colorScheme.secondary
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                                    },
                                            shape = CircleShape
                                    ),
                    contentAlignment = Alignment.Center
            ) {
                Icon(
                        imageVector = if (isGranted) Icons.Default.Check else Icons.Default.ArrowForward,
                        contentDescription =
                                stringResource(
                                        if (isGranted) {
                                            R.string.permission_guide_granted
                                        } else {
                                            R.string.permission_guide_not_granted
                                        }
                                ),
                        tint =
                                if (isGranted) {
                                    MaterialTheme.colorScheme.onSecondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        modifier = Modifier.size(17.dp)
                )
            }
            Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                )
                Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PermissionLevelPage(
        selectedLevel: AndroidPermissionLevel?,
        onLevelSelected: (AndroidPermissionLevel) -> Unit,
        onConfirm: () -> Unit
) {
    Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
                text = stringResource(R.string.permission_guide_level_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
                text = stringResource(R.string.permission_guide_level_desc),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 权限级别选择
        Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标准权限
            PermissionLevelItem(
                    level = AndroidPermissionLevel.STANDARD,
                    title = stringResource(R.string.permission_guide_standard_title),
                    description = stringResource(R.string.permission_guide_standard_desc),
                    isSelected = selectedLevel == AndroidPermissionLevel.STANDARD,
                    onClick = { onLevelSelected(AndroidPermissionLevel.STANDARD) }
            )

            // 无障碍权限
            PermissionLevelItem(
                    level = AndroidPermissionLevel.ACCESSIBILITY,
                    title = stringResource(R.string.permission_guide_accessibility_title),
                    description = stringResource(R.string.permission_guide_accessibility_desc),
                    isSelected = selectedLevel == AndroidPermissionLevel.ACCESSIBILITY,
                    onClick = { onLevelSelected(AndroidPermissionLevel.ACCESSIBILITY) }
            )

            // 调试权限
            PermissionLevelItem(
                    level = AndroidPermissionLevel.DEBUGGER,
                    title = stringResource(R.string.permission_guide_debugger_title),
                    description = stringResource(R.string.permission_guide_debugger_desc),
                    isSelected = selectedLevel == AndroidPermissionLevel.DEBUGGER,
                    onClick = { onLevelSelected(AndroidPermissionLevel.DEBUGGER) }
            )

            // Root权限
            PermissionLevelItem(
                    level = AndroidPermissionLevel.ROOT,
                    title = stringResource(R.string.permission_guide_root_title),
                    description = stringResource(R.string.permission_guide_root_desc),
                    isSelected = selectedLevel == AndroidPermissionLevel.ROOT,
                    onClick = { onLevelSelected(AndroidPermissionLevel.ROOT) }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 确认按钮
        Button(
                onClick = onConfirm,
                enabled = selectedLevel != null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                colors =
                        ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor =
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                disabledContentColor =
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
        ) {
            Text(
                    text = stringResource(R.string.permission_guide_confirm),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 提示文本
        Text(
                text = stringResource(R.string.permission_guide_change_anytime),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun PermissionLevelItem(
        level: AndroidPermissionLevel,
        title: String,
        description: String,
        isSelected: Boolean,
        onClick: () -> Unit
) {
    Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            shape = RoundedCornerShape(8.dp),
            color =
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.surface,
            border =
                    if (isSelected)
                            androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary
                            )
                    else null
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            // 选择指示器
            Box(
                    modifier =
                            Modifier.size(20.dp)
                                    .background(
                                            color =
                                                    if (isSelected)
                                                            MaterialTheme.colorScheme.primary
                                                    else Color.Transparent,
                                            shape = CircleShape
                                    )
                                    .border(
                                            width = 1.dp,
                                            color =
                                                    if (isSelected)
                                                            MaterialTheme.colorScheme.primary
                                                    else
                                                            MaterialTheme.colorScheme.onSurface
                                                                    .copy(alpha = 0.5f),
                                            shape = CircleShape
                                    ),
                    contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.permission_guide_selected),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color =
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                )

                Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}
