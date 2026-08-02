package com.crome.freshrss.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.crome.freshrss.data.model.FreshRssConfig
import com.crome.freshrss.data.model.ReadScope
import com.crome.freshrss.data.model.ReaderDefaults
import com.crome.freshrss.data.secure.SecureSecrets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "freshrss_settings")

class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val secrets = SecureSecrets(appContext)

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val USER = stringPreferencesKey("user")
        /** @deprecated plaintext — migrated to [SecureSecrets]. Kept only for one-time migrate. */
        val API_PASSWORD_LEGACY = stringPreferencesKey("api_password")
        val READ_SCOPE = stringPreferencesKey("read_scope")
        val ITEM_LIMIT = intPreferencesKey("item_limit")
        val PER_FEED_LIMIT = intPreferencesKey("per_feed_limit")
        /**
         * How many days of articles to download (default 30).
         * When > 0 this is the primary load limit (overrides item/per-feed caps).
         * Set to 0 to use Filters → per-feed / max article steppers instead.
         */
        val HISTORY_DAYS = intPreferencesKey("history_days")
        /** Place title + refresh + settings at bottom of screen. */
        val CHROME_AT_BOTTOM = booleanPreferencesKey("chrome_at_bottom")
        /**
         * Place Unread / All / Read / Starred / Video / Sound chips at the bottom
         * (just above the title bar when that is also bottom-aligned).
         */
        val SCOPE_CHIPS_AT_BOTTOM = booleanPreferencesKey("scope_chips_at_bottom")
        /**
         * Place the Filters expand/collapse panel (dates, search, download limits)
         * at the bottom of the list column.
         */
        val FILTERS_AT_BOTTOM = booleanPreferencesKey("filters_at_bottom")
        /**
         * Whether the Filters panel starts expanded when the app launches.
         * Home-screen toggle only affects the current session.
         */
        val EXPAND_FILTERS_ON_START = booleanPreferencesKey("expand_filters_on_start")
        /** Legacy key — migrated once into [EXPAND_FILTERS_ON_START]. */
        val FILTERS_EXPANDED_LEGACY = booleanPreferencesKey("filters_expanded")
        /**
         * When false (default), only https:// server URLs are accepted.
         * When true, http:// is allowed for LAN / Tailscale FreshRSS.
         */
        val ALLOW_CLEARTEXT_HTTP = booleanPreferencesKey("allow_cleartext_http")
        /** Show the Tailscale (key) action in the title bar. Default true. */
        val SHOW_TAILSCALE_BUTTON = booleanPreferencesKey("show_tailscale_button")
    }

    companion object {
        const val DEFAULT_HISTORY_DAYS = 30
    }

    /**
     * One-time move of plaintext API password from DataStore → EncryptedSharedPreferences.
     * Safe to call multiple times. Invoked from [com.crome.freshrss.FreshRssApp.onCreate].
     */
    fun migrateSecretsBlocking() {
        runBlocking {
            val prefs = appContext.dataStore.data.first()
            val legacy = prefs[Keys.API_PASSWORD_LEGACY]
            if (!legacy.isNullOrBlank() && secrets.getApiPassword().isBlank()) {
                secrets.setApiPassword(legacy)
            }
            if (prefs.contains(Keys.API_PASSWORD_LEGACY)) {
                appContext.dataStore.edit { it.remove(Keys.API_PASSWORD_LEGACY) }
            }
        }
    }

    val config: Flow<FreshRssConfig> = appContext.dataStore.data.map { prefs ->
        FreshRssConfig(
            // Prefer saved URL; never fall back to a hard-coded LAN IP.
            baseUrl = prefs[Keys.BASE_URL] ?: "",
            user = prefs[Keys.USER] ?: FreshRssConfig.DEFAULT_USER,
            // Always read from encrypted store (never from DataStore after migration).
            apiPassword = secrets.getApiPassword(),
        )
    }

    val readScope: Flow<ReadScope> = appContext.dataStore.data.map { prefs ->
        ReadScope.fromApi(prefs[Keys.READ_SCOPE] ?: ReaderDefaults.readScope.apiValue)
    }

    val itemLimit: Flow<Int> = appContext.dataStore.data.map { prefs ->
        prefs[Keys.ITEM_LIMIT] ?: ReaderDefaults.itemLimit
    }

    val perFeedLimit: Flow<Int> = appContext.dataStore.data.map { prefs ->
        prefs[Keys.PER_FEED_LIMIT] ?: ReaderDefaults.perFeedLimit
    }

    val historyDays: Flow<Int> = appContext.dataStore.data.map { prefs ->
        prefs[Keys.HISTORY_DAYS] ?: DEFAULT_HISTORY_DAYS
    }

    val chromeAtBottom: Flow<Boolean> = appContext.dataStore.data.map { prefs ->
        prefs[Keys.CHROME_AT_BOTTOM] ?: false
    }

    val scopeChipsAtBottom: Flow<Boolean> = appContext.dataStore.data.map { prefs ->
        prefs[Keys.SCOPE_CHIPS_AT_BOTTOM] ?: false
    }

    val filtersAtBottom: Flow<Boolean> = appContext.dataStore.data.map { prefs ->
        prefs[Keys.FILTERS_AT_BOTTOM] ?: false
    }

    /**
     * Start-up state for the Filters panel. Defaults to collapsed.
     * Migrates the old session-toggle key if present.
     */
    val expandFiltersOnStart: Flow<Boolean> = appContext.dataStore.data.map { prefs ->
        prefs[Keys.EXPAND_FILTERS_ON_START]
            ?: prefs[Keys.FILTERS_EXPANDED_LEGACY]
            ?: false
    }

    /** Default false — HTTPS-only until the user opts into cleartext HTTP. */
    val allowCleartextHttp: Flow<Boolean> = appContext.dataStore.data.map { prefs ->
        prefs[Keys.ALLOW_CLEARTEXT_HTTP] ?: false
    }

    /** Default true — Tailscale shortcut visible in the title bar. */
    val showTailscaleButton: Flow<Boolean> = appContext.dataStore.data.map { prefs ->
        prefs[Keys.SHOW_TAILSCALE_BUTTON] ?: true
    }

    suspend fun saveConfig(config: FreshRssConfig) {
        // Secret goes to Keystore-backed storage; never to plaintext DataStore.
        secrets.setApiPassword(config.apiPassword)
        appContext.dataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = config.normalizedBaseUrl
            prefs[Keys.USER] = config.user.trim()
            prefs.remove(Keys.API_PASSWORD_LEGACY)
        }
    }

    suspend fun setReadScope(scope: ReadScope) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.READ_SCOPE] = scope.apiValue
        }
    }

    suspend fun setItemLimit(n: Int) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.ITEM_LIMIT] = n.coerceIn(5, 500)
        }
    }

    suspend fun setPerFeedLimit(n: Int) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.PER_FEED_LIMIT] = n.coerceIn(1, 40)
        }
    }

    suspend fun setHistoryDays(n: Int) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.HISTORY_DAYS] = n.coerceIn(0, 365)
        }
    }

    suspend fun setChromeAtBottom(value: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.CHROME_AT_BOTTOM] = value
        }
    }

    suspend fun setScopeChipsAtBottom(value: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.SCOPE_CHIPS_AT_BOTTOM] = value
        }
    }

    suspend fun setFiltersAtBottom(value: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.FILTERS_AT_BOTTOM] = value
        }
    }

    suspend fun setExpandFiltersOnStart(value: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.EXPAND_FILTERS_ON_START] = value
            // Drop legacy key once the new preference is set.
            prefs.remove(Keys.FILTERS_EXPANDED_LEGACY)
        }
    }

    suspend fun setAllowCleartextHttp(value: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.ALLOW_CLEARTEXT_HTTP] = value
        }
    }

    suspend fun setShowTailscaleButton(value: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.SHOW_TAILSCALE_BUTTON] = value
        }
    }
}
