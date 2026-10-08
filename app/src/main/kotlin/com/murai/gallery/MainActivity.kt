package com.murai.gallery

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.murai.gallery.ui.nav.MuraiAppRoot
import com.murai.gallery.ui.recovery.CrashRecoveryHost
import com.murai.gallery.ui.theme.MuraiTheme
import com.murai.gallery.ui.theme.ThemeMode
import com.murai.gallery.util.ConsentBus
import com.murai.gallery.util.ErrorLogger
import com.murai.gallery.util.IncomingIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Single-activity host. Edge-to-edge is enabled here and every screen applies
 * its own safe insets, which is what keeps toolbars and rows from clipping
 * under the status bar or navigation bar.
 *
 * v2.0.1 hardening:
 *  - The persisted app language is applied via AppCompatDelegate on the MAIN
 *    thread before setContent (the API is main-thread-only; calling it from a
 *    background dispatcher is what crashed v2.0.0 launches).
 *  - Incoming ACTION_VIEW / SEND / SEND_MULTIPLE / SET_WALLPAPER intents are
 *    parsed once into an [IncomingIntent] and re-parsed in onNewIntent, so
 *    "open with" works while the app is already running.
 *  - Consent requests flow through the one-shot ConsentBus queue: each
 *    IntentSender launches at most once, and every callback completes even
 *    when the launch fails or the activity goes away.
 *  - After a fatal crash the next launch shows the recovery screen (copy log,
 *    export log zip) instead of crashing in a loop.
 */
class MainActivity : AppCompatActivity() {

    private val incoming = MutableStateFlow<IncomingIntent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Restore the persisted language on the main thread, before UI. The
        // Application publishes the saved tag asynchronously, so wait briefly
        // for it (blank = system default, published immediately).
        lifecycleScope.launch {
            val tag = runCatching {
                kotlinx.coroutines.withTimeoutOrNull(2000) {
                    MuraiApp.savedLanguage.first { it != null }
                }
            }.getOrNull() ?: ""
            if (tag.isNotBlank() &&
                AppCompatDelegate.getApplicationLocales().toLanguageTags() != tag
            ) {
                runCatching {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                }.onFailure { ErrorLogger.write(this@MainActivity, "locale-apply", it) }
            }
        }

        incoming.value = IncomingIntent.from(intent)

        val container = MuraiApp.get(this).container

        setContent {
            // Scoped-storage consent (delete/trash/write on shared media):
            // every queued request is launched exactly once here.
            val consentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                ConsentBus.complete(result.resultCode == RESULT_OK)
            }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                ConsentBus.requests.collect { pendingRequest ->
                    if (pendingRequest.sender == null) {
                        ConsentBus.complete(pendingRequest, false)
                        return@collect
                    }
                    ConsentBus.setActive(pendingRequest)
                    runCatching {
                        consentLauncher.launch(
                            IntentSenderRequest.Builder(pendingRequest.sender).build()
                        )
                    }.onFailure {
                        ErrorLogger.write(this@MainActivity, "consent-launch", it)
                        ConsentBus.complete(pendingRequest, false)
                    }
                }
            }
            val themeMode = container.settings.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM).value
            val amoled = container.settings.amoled
                .collectAsStateWithLifecycle(initialValue = false).value
            val dynamic = container.settings.dynamicColor
                .collectAsStateWithLifecycle(initialValue = true).value
            val currentIncoming by incoming.collectAsState()
            MuraiTheme(themeMode = themeMode, amoled = amoled, dynamicColor = dynamic) {
                CrashRecoveryHost(container = container) {
                    MuraiAppRoot(container = container, incomingIntent = currentIncoming)
                }
            }
        }
    }

    /** Handles VIEW/SEND/etc. while the activity is already alive. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // setIntent keeps the new data for future re-parses.
        runCatching { setIntent(intent) }
            .onFailure { ErrorLogger.write(this, "set-intent", it) }
        incoming.value = IncomingIntent.from(intent)
    }

    override fun onDestroy() {
        // Every consent callback must complete even when the host goes away.
        ConsentBus.cancelAll()
        super.onDestroy()
    }
}
