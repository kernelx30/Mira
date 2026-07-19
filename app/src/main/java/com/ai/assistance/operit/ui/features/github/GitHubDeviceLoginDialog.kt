package com.ai.assistance.operit.ui.features.github

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

@Composable
fun GitHubDeviceLoginDialog(
    onDismissRequest: () -> Unit,
    onLoginSuccess: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coordinator = remember { GitHubOAuthCoordinator(context) }
    var attempt by rememberSaveable { mutableIntStateOf(0) }
    var isRequestingCode by remember { mutableStateOf(true) }
    var session by remember { mutableStateOf<GitHubDeviceLoginSession?>(null) }
    var loginError by remember { mutableStateOf<Throwable?>(null) }
    var browserError by remember { mutableStateOf<String?>(null) }
    val dismissLogin = {
        session = null
        onDismissRequest()
    }

    LaunchedEffect(attempt) {
        isRequestingCode = true
        session = null
        loginError = null
        browserError = null
        coordinator.startDeviceLogin().fold(
            onSuccess = {
                session = it
                isRequestingCode = false
            },
            onFailure = {
                loginError = it
                isRequestingCode = false
            }
        )
    }

    LaunchedEffect(session?.deviceCode) {
        val activeSession = session ?: return@LaunchedEffect
        coordinator.completeDeviceLogin(activeSession).fold(
            onSuccess = { user ->
                Toast.makeText(
                    context,
                    context.getString(R.string.main_github_login_success, user.login),
                    Toast.LENGTH_LONG
                ).show()
                onLoginSuccess?.invoke()
                dismissLogin()
            },
            onFailure = { loginError = it }
        )
    }

    AlertDialog(
        onDismissRequest = dismissLogin,
        title = { Text(stringResource(R.string.login_github)) },
        text = {
            when {
                isRequestingCode -> GitHubDeviceCodeLoadingContent()
                loginError != null -> GitHubDeviceLoginErrorContent(
                    error = requireNotNull(loginError)
                )
                session != null -> GitHubDeviceCodeContent(
                    session = requireNotNull(session),
                    browserError = browserError,
                    onCopyCode = { copyDeviceCode(context, requireNotNull(session).userCode) },
                    onOpenGitHub = {
                        browserError = openVerificationPage(context, requireNotNull(session).verificationUri)
                    }
                )
            }
        },
        confirmButton = {
            when {
                loginError != null -> Button(onClick = { attempt += 1 }) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.github_device_login_retry))
                }
                session != null -> Button(
                    onClick = {
                        browserError = openVerificationPage(context, requireNotNull(session).verificationUri)
                    }
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.github_device_login_open_github))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = dismissLogin) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun GitHubDeviceCodeLoadingContent() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.github_device_login_requesting_code))
    }
}

@Composable
private fun GitHubDeviceCodeContent(
    session: GitHubDeviceLoginSession,
    browserError: String?,
    onCopyCode: () -> Unit,
    onOpenGitHub: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.github_device_login_instructions),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            SelectionContainer {
                Text(
                    text = session.userCode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCopyCode,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.github_device_login_copy_code))
            }
            OutlinedButton(
                onClick = onOpenGitHub,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.github_device_login_open_github))
            }
        }
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(
                R.string.github_device_login_waiting,
                ((session.expiresInSeconds + 59L) / 60L).coerceAtLeast(1L)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (browserError != null) {
            Text(
                text = browserError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun GitHubDeviceLoginErrorContent(error: Throwable) {
    val context = LocalContext.current
    val message = when ((error as? GitHubDeviceFlowException)?.failure) {
        GitHubDeviceFlowFailure.CLIENT_ID_MISSING ->
            stringResource(R.string.github_device_login_client_id_missing)
        GitHubDeviceFlowFailure.AUTHORIZATION_EXPIRED ->
            stringResource(R.string.github_device_login_expired)
        GitHubDeviceFlowFailure.ACCESS_DENIED ->
            stringResource(R.string.github_device_login_denied)
        GitHubDeviceFlowFailure.TOKEN_REJECTED ->
            stringResource(R.string.github_device_login_rejected)
        GitHubDeviceFlowFailure.TOKEN_VERIFICATION_FAILED ->
            stringResource(R.string.github_device_login_verification_failed)
        GitHubDeviceFlowFailure.INSUFFICIENT_SCOPE ->
            stringResource(R.string.github_device_login_insufficient_scope)
        null -> context.getString(
            R.string.main_github_login_failed,
            error.message.orEmpty()
        )
    }

    Text(text = message, color = MaterialTheme.colorScheme.error)
}

private fun copyDeviceCode(context: Context, userCode: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(
        context.getString(R.string.github_device_login_code_label),
        userCode
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
    Toast.makeText(
        context,
        context.getString(R.string.github_device_login_code_copied),
        Toast.LENGTH_SHORT
    ).show()
}

private fun openVerificationPage(context: Context, verificationUri: String): String? {
    return try {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(verificationUri)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val handlers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(
                browserIntent,
                android.content.pm.PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(browserIntent, 0)
        }
        val externalHandlers = handlers.filter { it.activityInfo.packageName != context.packageName }
        if (externalHandlers.isEmpty()) {
            return context.getString(R.string.github_device_login_browser_failed)
        }

        val ownComponents = handlers
            .filter { it.activityInfo.packageName == context.packageName }
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
            .toTypedArray()
        val chooser = Intent.createChooser(
            browserIntent,
            context.getString(R.string.login_github)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (ownComponents.isNotEmpty()) {
                putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, ownComponents)
            }
        }
        context.startActivity(chooser)
        null
    } catch (error: Exception) {
        context.getString(R.string.github_device_login_browser_failed)
    }
}
