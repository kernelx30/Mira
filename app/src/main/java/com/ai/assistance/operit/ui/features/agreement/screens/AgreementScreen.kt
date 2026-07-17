package com.ai.assistance.operit.ui.features.agreement.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.MiraLogo
import kotlinx.coroutines.delay

@Composable
fun AgreementScreen(onAgreementAccepted: () -> Unit) {
    var isButtonEnabled by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableIntStateOf(5) }

    LaunchedEffect(Unit) {
        repeat(5) {
            delay(1_000)
            remainingSeconds -= 1
        }
        isButtonEnabled = true
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            AgreementBrandBar()
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val wideLayout = maxWidth >= 840.dp
                if (wideLayout) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 1180.dp)
                            .align(Alignment.Center)
                            .padding(horizontal = 32.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(40.dp)
                    ) {
                        AgreementOverview(
                            modifier = Modifier
                                .width(320.dp)
                                .fillMaxHeight()
                        )
                        AgreementDocument(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 720.dp)
                            .align(Alignment.Center)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        AgreementOverview()
                        AgreementDocument(
                            modifier = Modifier.heightIn(min = 420.dp),
                            managesOwnScroll = false
                        )
                    }
                }
            }
            AgreementActionBar(
                enabled = isButtonEnabled,
                remainingSeconds = remainingSeconds,
                onAccept = onAgreementAccepted
            )
        }
    }
}

@Composable
private fun AgreementBrandBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 24.dp, vertical = 14.dp),
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
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.mate_agreement_eyebrow),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AgreementOverview(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.agreement_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.agreement_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AgreementBoundary(
            icon = Icons.Default.Lock,
            title = stringResource(R.string.mate_agreement_local_title),
            description = stringResource(R.string.mate_agreement_local_desc)
        )
        AgreementBoundary(
            icon = Icons.Default.Cloud,
            title = stringResource(R.string.mate_agreement_provider_title),
            description = stringResource(R.string.mate_agreement_provider_desc)
        )
        AgreementBoundary(
            icon = Icons.Default.Security,
            title = stringResource(R.string.mate_agreement_permission_title),
            description = stringResource(R.string.mate_agreement_permission_desc)
        )
    }
}

@Composable
private fun AgreementBoundary(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(19.dp)
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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AgreementDocument(
    modifier: Modifier = Modifier,
    managesOwnScroll: Boolean = true
) {
    val scrollModifier =
        if (managesOwnScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = scrollModifier.padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.agreement_human_readable_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.agreement_human_readable_content),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = stringResource(R.string.agreement_serious_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            AgreementHtmlText()
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = stringResource(R.string.agreement_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AgreementHtmlText() {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val typography = MaterialTheme.typography.bodyMedium
    AndroidView(
        factory = { context ->
            android.widget.TextView(context).apply {
                setTextColor(textColor.toArgb())
                textSize = typography.fontSize.value
                val targetLineHeight =
                    (typography.lineHeight.value * context.resources.displayMetrics.scaledDensity).toInt()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    lineHeight = targetLineHeight
                } else {
                    setLineSpacing(
                        targetLineHeight - paint.fontMetricsInt.descent + paint.fontMetricsInt.ascent.toFloat(),
                        1f
                    )
                }
            }
        },
        update = { textView ->
            textView.setTextColor(textColor.toArgb())
            textView.text =
                HtmlCompat.fromHtml(
                    textView.context.getString(R.string.agreement_serious_content),
                    HtmlCompat.FROM_HTML_MODE_COMPACT
                )
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AgreementActionBar(
    enabled: Boolean,
    remainingSeconds: Int,
    onAccept: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onAccept,
                enabled = enabled,
                modifier = Modifier
                    .widthIn(min = 220.dp, max = 360.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text =
                        if (enabled) {
                            stringResource(R.string.agreement_accept)
                        } else {
                            stringResource(R.string.agreement_wait, remainingSeconds)
                        },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
