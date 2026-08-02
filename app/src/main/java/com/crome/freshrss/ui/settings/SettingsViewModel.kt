package com.crome.freshrss.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.crome.freshrss.data.model.FreshRssConfig
import com.crome.freshrss.data.prefs.SettingsRepository
import com.crome.freshrss.data.remote.FreshRssClient
import com.crome.freshrss.ui.theme.AppThemeMode
import com.crome.freshrss.util.ServerUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseUrl: String = "",
    val user: String = FreshRssConfig.DEFAULT_USER,
    val apiPassword: String = "",
    val itemLimit: Int = FreshRssConfig.DEFAULT_ITEM_LIMIT,
    val perFeedLimit: Int = FreshRssConfig.DEFAULT_PER_FEED_LIMIT,
    /** Days of history to download; >0 overrides item/per-feed limits. Default 30. */
    val historyDays: Int = SettingsRepository.DEFAULT_HISTORY_DAYS,
    val chromeAtBottom: Boolean = false,
    /** Place scope/media chips at the bottom (above title bar when bottom-aligned). */
    val scopeChipsAtBottom: Boolean = false,
    /** Place Filters panel (dates/search/download) at the bottom. */
    val filtersAtBottom: Boolean = false,
    /** Filters panel expanded when the app launches. */
    val expandFiltersOnStart: Boolean = false,
    /** Allow http:// server URLs (LAN). Default false = HTTPS only. */
    val allowCleartextHttp: Boolean = false,
    /** Show Tailscale shortcut in the title bar. */
    val showTailscaleButton: Boolean = true,
    /** System / Light / Dark */
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val testResult: String? = null,
    val testing: Boolean = false,
    val saved: Boolean = false,
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val client: FreshRssClient,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val cfg = settings.config.first()
            _state.update {
                it.copy(
                    baseUrl = cfg.baseUrl,
                    user = cfg.user,
                    apiPassword = cfg.apiPassword,
                    itemLimit = settings.itemLimit.first(),
                    perFeedLimit = settings.perFeedLimit.first(),
                    historyDays = settings.historyDays.first(),
                    chromeAtBottom = settings.chromeAtBottom.first(),
                    scopeChipsAtBottom = settings.scopeChipsAtBottom.first(),
                    filtersAtBottom = settings.filtersAtBottom.first(),
                    expandFiltersOnStart = settings.expandFiltersOnStart.first(),
                    allowCleartextHttp = settings.allowCleartextHttp.first(),
                    showTailscaleButton = settings.showTailscaleButton.first(),
                    themeMode = settings.themeMode.first(),
                )
            }
        }
    }

    fun updateBaseUrl(v: String) = _state.update { it.copy(baseUrl = v, saved = false) }
    fun updateUser(v: String) = _state.update { it.copy(user = v, saved = false) }
    fun updatePassword(v: String) = _state.update { it.copy(apiPassword = v, saved = false) }
    fun updateItemLimit(v: String) {
        v.toIntOrNull()?.let { n -> _state.update { it.copy(itemLimit = n, saved = false) } }
    }
    fun updatePerFeed(v: String) {
        v.toIntOrNull()?.let { n -> _state.update { it.copy(perFeedLimit = n, saved = false) } }
    }
    fun updateHistoryDays(v: String) {
        v.toIntOrNull()?.let { n -> _state.update { it.copy(historyDays = n, saved = false) } }
    }
    fun updateChromeAtBottom(v: Boolean) = _state.update {
        it.copy(chromeAtBottom = v, saved = false)
    }
    fun updateScopeChipsAtBottom(v: Boolean) = _state.update {
        it.copy(scopeChipsAtBottom = v, saved = false)
    }
    fun updateFiltersAtBottom(v: Boolean) = _state.update {
        it.copy(filtersAtBottom = v, saved = false)
    }
    fun updateExpandFiltersOnStart(v: Boolean) = _state.update {
        it.copy(expandFiltersOnStart = v, saved = false)
    }
    fun updateAllowCleartextHttp(v: Boolean) = _state.update {
        it.copy(allowCleartextHttp = v, saved = false)
    }
    fun updateShowTailscaleButton(v: Boolean) = _state.update {
        it.copy(showTailscaleButton = v, saved = false)
    }

    /** Applies immediately so the UI previews the choice before Save. */
    fun updateThemeMode(mode: AppThemeMode) {
        _state.update { it.copy(themeMode = mode, saved = false) }
        viewModelScope.launch {
            settings.setThemeMode(mode)
        }
    }

    /**
     * Persist settings. [onSuccess] runs only after a valid save (normalized URL).
     */
    fun save(onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            val s = _state.value
            val url = ServerUrl.normalize(s.baseUrl, s.allowCleartextHttp)
            if (!url.ok) {
                _state.update {
                    it.copy(saved = false, testResult = "Failed: ${url.error}")
                }
                return@launch
            }
            val cfg = FreshRssConfig(
                baseUrl = url.normalized,
                user = s.user,
                apiPassword = s.apiPassword,
            )
            settings.saveConfig(cfg)
            settings.setItemLimit(s.itemLimit)
            settings.setPerFeedLimit(s.perFeedLimit)
            settings.setHistoryDays(s.historyDays)
            settings.setChromeAtBottom(s.chromeAtBottom)
            settings.setScopeChipsAtBottom(s.scopeChipsAtBottom)
            settings.setFiltersAtBottom(s.filtersAtBottom)
            settings.setExpandFiltersOnStart(s.expandFiltersOnStart)
            settings.setAllowCleartextHttp(s.allowCleartextHttp)
            settings.setShowTailscaleButton(s.showTailscaleButton)
            settings.setThemeMode(s.themeMode)
            client.config = cfg
            _state.update {
                it.copy(
                    baseUrl = url.normalized,
                    saved = true,
                    testResult = null,
                )
            }
            onSuccess?.invoke()
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _state.update { it.copy(testing = true, testResult = null) }
            val s = _state.value
            val url = ServerUrl.normalize(s.baseUrl, s.allowCleartextHttp)
            if (!url.ok) {
                _state.update {
                    it.copy(testing = false, testResult = "Failed: ${url.error}")
                }
                return@launch
            }
            client.config = FreshRssConfig(
                baseUrl = url.normalized,
                user = s.user,
                apiPassword = s.apiPassword,
            )
            if (s.apiPassword.isBlank()) {
                _state.update {
                    it.copy(
                        testing = false,
                        testResult = "Failed: API password empty",
                    )
                }
                return@launch
            }
            val status = client.status()
            if (!status.ok) {
                _state.update {
                    it.copy(testing = false, testResult = "Failed status: ${status.error}")
                }
                return@launch
            }
            val all = client.items(
                limit = s.itemLimit,
                scope = com.crome.freshrss.data.model.ReadScope.ALL,
                perFeed = s.perFeedLimit,
                historyDays = s.historyDays,
            )
            val msg = if (!all.ok) {
                "Status OK but All failed: ${all.error}"
            } else {
                val schemeNote = if (url.isCleartext) " · HTTP" else " · HTTPS"
                "OK · unread=${status.unread} · All shown=${all.items.size} · " +
                    "feeds=${all.feeds.size} · source=${all.source} · ms=${all.elapsedMs}$schemeNote"
            }
            _state.update { it.copy(testing = false, testResult = msg) }
        }
    }

    companion object {
        fun factory(settings: SettingsRepository, client: FreshRssClient) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(settings, client) as T
            }
    }
}
