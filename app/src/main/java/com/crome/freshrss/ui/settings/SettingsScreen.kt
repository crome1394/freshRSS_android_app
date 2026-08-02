package com.crome.freshrss.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crome.freshrss.ui.theme.AppThemeMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit,
    /** First launch with no server URL — show setup intro and primary “Save & continue”. */
    firstRun: Boolean = false,
    onSetupComplete: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (firstRun) "Set up FreshRSS" else "Settings") },
                navigationIcon = {
                    if (!firstRun) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (firstRun) {
                Text(
                    "Welcome",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Enter your self-hosted FreshRSS server. " +
                        "URLs default to HTTPS. Enable “Allow insecure HTTP” only for trusted LAN/VPN.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            } else {
                Text(
                    "Same fields as ~/.config/quickshell/secrets/freshrss.env",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = vm::updateBaseUrl,
                label = { Text("FRESHRSS_BASE_URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://freshrss.example.com") },
                supportingText = {
                    Text(
                        "Required. Prefer https://. Bare hosts get https:// automatically.",
                    )
                },
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.user,
                onValueChange = vm::updateUser,
                label = { Text("FRESHRSS_USER") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("e.g. admin or Javier") },
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.apiPassword,
                onValueChange = vm::updatePassword,
                label = { Text("FRESHRSS_API_PASSWORD") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = {
                    Text("Profile → API password (not the web form password).")
                },
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text("Security", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Allow insecure HTTP")
                    Text(
                        "Permit http:// server URLs (LAN / Tailscale). " +
                            "Off by default — credentials should travel over HTTPS when possible.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.allowCleartextHttp,
                    onCheckedChange = vm::updateAllowCleartextHttp,
                )
            }

            if (!firstRun) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.historyDays.toString(),
                    onValueChange = vm::updateHistoryDays,
                    label = { Text("History (days)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text(
                            "Default 30. Downloads articles from the last N days and " +
                                "overrides Filters → per-feed / max article limits. " +
                                "Set to 0 to use those steppers instead.",
                        )
                    },
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.itemLimit.toString(),
                    onValueChange = vm::updateItemLimit,
                    label = { Text("Item limit (Unread/Starred, when history = 0)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = state.historyDays == 0,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.perFeedLimit.toString(),
                    onValueChange = vm::updatePerFeed,
                    label = { Text("Per-feed limit (All/Read, when history = 0)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = state.historyDays == 0,
                )

                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Theme",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Light is easier in bright rooms; Dark matches the Tokyo Night look. " +
                        "System follows your phone setting.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (mode in AppThemeMode.entries) {
                        FilterChip(
                            selected = state.themeMode == mode,
                            onClick = { vm.updateThemeMode(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text("Layout", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LayoutSwitchRow(
                    title = "Title bar at bottom",
                    subtitle = "Move FreshRSS title, refresh, and settings to the bottom",
                    checked = state.chromeAtBottom,
                    onCheckedChange = vm::updateChromeAtBottom,
                )
                Spacer(Modifier.height(12.dp))
                LayoutSwitchRow(
                    title = "Filter chips at bottom",
                    subtitle = "Move Unread / All / Read / Starred / Video / Sound chips " +
                        "to the bottom, just above the title bar when it is also bottom-aligned",
                    checked = state.scopeChipsAtBottom,
                    onCheckedChange = vm::updateScopeChipsAtBottom,
                )
                Spacer(Modifier.height(12.dp))
                LayoutSwitchRow(
                    title = "Filters panel at bottom",
                    subtitle = "Move the Filters expand/collapse section (dates, search, download limits) " +
                        "to the bottom of the article list",
                    checked = state.filtersAtBottom,
                    onCheckedChange = vm::updateFiltersAtBottom,
                )
                Spacer(Modifier.height(12.dp))
                LayoutSwitchRow(
                    title = "Expand filters on start",
                    subtitle = "Open the Filters panel when the app launches. " +
                        "Toggling Filters on the home screen only affects the current session.",
                    checked = state.expandFiltersOnStart,
                    onCheckedChange = vm::updateExpandFiltersOnStart,
                )
                Spacer(Modifier.height(12.dp))
                LayoutSwitchRow(
                    title = "Show Tailscale button",
                    subtitle = "Show the key icon in the title bar to open the Tailscale app",
                    checked = state.showTailscaleButton,
                    onCheckedChange = vm::updateShowTailscaleButton,
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (firstRun) {
                        vm.save(onSuccess = onSetupComplete)
                    } else {
                        vm.save()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        firstRun -> "Save & continue"
                        state.saved -> "Saved"
                        else -> "Save"
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.testConnection() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.testing,
            ) {
                Text(if (state.testing) "Testing…" else "Test connection")
            }
            state.testResult?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it.startsWith("OK")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }
}

@Composable
private fun LayoutSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
