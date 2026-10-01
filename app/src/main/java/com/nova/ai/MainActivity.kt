package com.nova.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.ai.data.datastore.AppSettings
import com.nova.ai.data.datastore.SettingsStore
import com.nova.ai.ui.navigation.MainScreen
import com.nova.ai.ui.theme.NovaTheme
import com.nova.ai.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-activity host for the Nova AI Chat & Agent Workspace.
 *
 * Reads the persisted theme setting and applies [NovaTheme] before showing
 * the [MainScreen] navigation scaffold.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsStore: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsStore.settings.collectAsStateWithLifecycle(AppSettings())
            val themeMode = when (settings.themeMode) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            NovaTheme(themeMode = themeMode) {
                MainScreen()
            }
        }
    }
}
