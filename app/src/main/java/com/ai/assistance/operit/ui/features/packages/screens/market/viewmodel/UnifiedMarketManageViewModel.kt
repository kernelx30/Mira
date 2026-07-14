package com.ai.assistance.operit.ui.features.packages.screens.market.viewmodel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2PublisherEntrySummary
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.ui.features.packages.market.MarketStatsType
import com.ai.assistance.operit.ui.features.packages.market.MarketReviewState
import com.ai.assistance.operit.ui.features.packages.market.resolveMarketReviewSnapshot
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class UnifiedMarketManageKind(val types: Set<String>) {
    SCRIPT(setOf(MarketStatsType.SCRIPT.wireValue)),
    PACKAGE(setOf(MarketStatsType.PACKAGE.wireValue)),
    ARTIFACT(setOf(MarketStatsType.SCRIPT.wireValue, MarketStatsType.PACKAGE.wireValue)),
    SKILL(setOf(MarketStatsType.SKILL.wireValue)),
    MCP(setOf(MarketStatsType.MCP.wireValue))
}

class UnifiedMarketManageViewModel(
    private val context: Context,
    private val kind: UnifiedMarketManageKind
) : ViewModel() {
    private val marketStatsApiService = MarketStatsApiService()
    private val githubAuth = GitHubAuthPreferences.getInstance(context)

    private val _isLoading = MutableStateFlow(false)
   val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _entries = MutableStateFlow<List<MarketV2PublisherEntrySummary>>(emptyList())
    val entries: StateFlow<List<MarketV2PublisherEntrySummary>> = _entries.asStateFlow()

    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()

    private val _resubmittingEntryId = MutableStateFlow<String?>(null)
    val resubmittingEntryId: StateFlow<String?> = _resubmittingEntryId.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> =
        githubAuth.isLoggedInFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun loadEntries(refresh: Boolean = false) {
       viewModelScope.launch {
           if (_isLoading.value) return@launch
            if (refresh && _isRefreshing.value) return@launch
           if (!refresh && _hasLoaded.value) return@launch
            if (!githubAuth.isLoggedIn()) {
                _errorMessage.value = context.getString(R.string.skillmarket_github_login_required)
                return@launch
            }

            if (refresh) {
                _isRefreshing.value = true
            } else {
                _isLoading.value = true
            }
            _errorMessage.value = null

           try {
                val userInfo = githubAuth.getCurrentUserInfo()
                if (userInfo == null) {
                    _errorMessage.value = context.getString(R.string.skillmarket_unable_get_user_info)
                    return@launch
                }

                val loaded =
                    withContext(Dispatchers.IO) {
                        kind.types
                            .flatMap { type ->
                                marketStatsApiService.getUserPublishedEntries(type).getOrThrow()
                            }
                            .filter { entry -> entry.type.lowercase() in kind.types }
                            .distinctBy { it.id }
                            .sortedByDescending { it.updatedAt }
                    }
                recordVisibleReviewLocks(loaded)
                _entries.value = loaded
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: context.getString(R.string.market_error_load_failed)
                AppLogger.e(TAG, "Failed to load managed market entries", e)
            } finally {
                _hasLoaded.value = true
            if (refresh) {
                _isRefreshing.value = false
            } else {
                _isLoading.value = false
            }
            }
        }
    }

    fun reset() {
       _entries.value = emptyList()
       _hasLoaded.value = false
       _errorMessage.value = null
        _isRefreshing.value = false
   }

    fun clearError() {
        _errorMessage.value = null
    }

    fun openEntryDetail(
        entry: MarketV2PublisherEntrySummary,
        onLoaded: (MarketV2Entry) -> Unit
    ) {
        viewModelScope.launch {
            if (entry.id.isBlank()) {
                _errorMessage.value = context.getString(R.string.skillmarket_remove_failed, "entry not found")
                return@launch
            }

            _isLoading.value = true
            _errorMessage.value = null
            try {
                val fullEntry =
                    withContext(Dispatchers.IO) {
                        marketStatsApiService.getEntry(entry.id).getOrThrow()
                    }
                if (fullEntry == null) {
                    _errorMessage.value = context.getString(R.string.skillmarket_remove_failed, "entry not found")
                    return@launch
                }
                onLoaded(fullEntry)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: context.getString(R.string.market_error_load_failed)
                AppLogger.e(TAG, "Failed to load full managed market entry ${entry.id}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun withdrawEntry(entry: MarketV2PublisherEntrySummary) {
        updateEntryState(
            entry = entry,
            stateCode = "withdrawn",
            action = { marketStatsApiService.withdrawEntry(entry.id) },
            successMessage = context.getString(R.string.market_manage_removed, entry.title)
        )
    }

    fun resubmitEntry(entry: MarketV2PublisherEntrySummary) {
        val blockMessage = resolveResubmitBlockMessage(entry)
        if (blockMessage != null) {
            _errorMessage.value = blockMessage
            Toast.makeText(context, blockMessage, Toast.LENGTH_SHORT).show()
            return
        }

        updateEntryState(
            entry = entry,
            stateCode = "pending",
            action = { marketStatsApiService.resubmitEntry(entry.id) },
            successMessage = context.getString(R.string.market_manage_resubmitted, entry.title),
            loadingEntryId = entry.id
        )
    }

    private fun updateEntryState(
        entry: MarketV2PublisherEntrySummary,
        stateCode: String,
        action: suspend () -> Result<MarketV2Entry>,
        successMessage: String,
        onSuccess: (() -> Unit)? = null,
        loadingEntryId: String? = null
    ) {
        viewModelScope.launch {
            if (!githubAuth.isLoggedIn()) {
                _errorMessage.value = context.getString(R.string.skillmarket_github_login_required)
                return@launch
            }
            if (entry.id.isBlank()) {
                _errorMessage.value = context.getString(R.string.skillmarket_remove_failed, "entry not found")
                return@launch
            }

            _isLoading.value = true
            if (loadingEntryId != null) {
                _resubmittingEntryId.value = loadingEntryId
            }
            _errorMessage.value = null
            try {
                action().fold(
                    onSuccess = {
                        _entries.value =
                            _entries.value.map { existing ->
                                if (existing.id == entry.id) existing.copy(stateCode = stateCode) else existing
                            }
                        onSuccess?.invoke()
                        Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { error ->
                        _errorMessage.value = error.message ?: context.getString(R.string.market_error_action_failed)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: context.getString(R.string.market_error_action_failed)
                AppLogger.e(TAG, "Failed to update managed market entry", e)
            } finally {
                if (loadingEntryId != null && _resubmittingEntryId.value == loadingEntryId) {
                    _resubmittingEntryId.value = null
                }
                _isLoading.value = false
            }
        }
    }

    private fun resolveResubmitBlockMessage(entry: MarketV2PublisherEntrySummary): String? {
        val review = entry.resolveMarketReviewSnapshot()
        if (review.state == MarketReviewState.REJECTED || resubmitPreferences().getBoolean(rejectedKey(entry.id), false)) {
            return context.getString(R.string.market_manage_resubmit_rejected_blocked)
        }
        if (review.state != MarketReviewState.CHANGES_REQUESTED) {
            return context.getString(R.string.market_manage_resubmit_state_blocked)
        }

        val cooldownUntil = getResubmitCooldownUntil(entry)
        val now = System.currentTimeMillis()
        if (cooldownUntil > now) {
            return context.getString(
                R.string.market_manage_resubmit_cooldown_message,
                formatRemainingCooldown(cooldownUntil - now)
            )
        }
        return null
    }

    private fun recordVisibleReviewLocks(entries: List<MarketV2PublisherEntrySummary>) {
        val preferences = resubmitPreferences()
        val editor = preferences.edit()
        for (entry in entries) {
            when (entry.resolveMarketReviewSnapshot().state) {
                MarketReviewState.CHANGES_REQUESTED -> {
                    val key = cooldownKey(entry)
                    if (!preferences.contains(key)) {
                        editor.putLong(key, reviewCooldownUntil(entry))
                    }
                }
                MarketReviewState.REJECTED -> {
                    editor.putBoolean(rejectedKey(entry.id), true)
                }
                MarketReviewState.PENDING,
                MarketReviewState.APPROVED,
                MarketReviewState.WITHDRAWN -> Unit
            }
        }
        editor.apply()
    }

    private fun getResubmitCooldownUntil(entry: MarketV2PublisherEntrySummary): Long {
        return resubmitPreferences().getLong(cooldownKey(entry), 0L)
    }

    private fun reviewCooldownUntil(entry: MarketV2PublisherEntrySummary): Long {
        return java.time.Instant.parse(entry.updatedAt).toEpochMilli() + RESUBMIT_COOLDOWN_MILLIS
    }

    private fun resubmitPreferences() =
        context.getSharedPreferences(RESUBMIT_PREFS_NAME, Context.MODE_PRIVATE)

    private fun cooldownKey(entry: MarketV2PublisherEntrySummary): String {
        return "changes_requested_until_${entry.id}_${entry.updatedAt}"
    }

    private fun rejectedKey(entryId: String): String {
        return "rejected_$entryId"
    }

    private fun formatRemainingCooldown(remainingMillis: Long): String {
        val totalMinutes = ((remainingMillis + 59_999L) / 60_000L).coerceAtLeast(1L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours > 0L && minutes > 0L -> "${hours}小时${minutes}分钟"
            hours > 0L -> "${hours}小时"
            else -> "${minutes}分钟"
        }
    }

    class Factory(
        private val context: Context,
        private val kind: UnifiedMarketManageKind
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(UnifiedMarketManageViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return UnifiedMarketManageViewModel(context, kind) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private const val TAG = "UnifiedMarketManageViewModel"
        private const val RESUBMIT_PREFS_NAME = "market_resubmit_cooldowns"
        private const val RESUBMIT_COOLDOWN_MILLIS = 12L * 60L * 60L * 1000L
    }
}


