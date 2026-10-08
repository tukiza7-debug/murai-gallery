package com.murai.gallery

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.murai.gallery.ui.nav.MuraiAppRoot
import com.murai.gallery.ui.theme.MuraiTheme
import com.murai.gallery.ui.theme.ThemeMode

/**
 * Single-activity host. Edge-to-edge is enabled here and every screen applies
 * its own safe insets, which is what keeps toolbars and rows from clipping
 * under the status bar or navigation bar.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = MuraiApp.get(this).container

        val incomingUri = if (intent?.action == android.content.Intent.ACTION_VIEW) intent.data else null

        setContent {
            // Scoped-storage consent (delete/trash on shared media) launches here
            val consentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                com.murai.gallery.util.ConsentBus.complete(result.resultCode == android.app.Activity.RESULT_OK)
            }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                com.murai.gallery.util.ConsentBus.pending.collect { pendingRequest ->
                    pendingRequest?.let {
                        runCatching {
                            consentLauncher.launch(
                                androidx.activity.result.IntentSenderRequest.Builder(it.sender).build()
                            )
                        }.onFailure { com.murai.gallery.util.ConsentBus.complete(false) }
                    }
                }
            }
            val themeMode = container.settings.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM).value
            val amoled = container.settings.amoled
                .collectAsStateWithLifecycle(initialValue = false).value
            val dynamic = container.settings.dynamicColor
                .collectAsStateWithLifecycle(initialValue = true).value
            MuraiTheme(themeMode = themeMode, amoled = amoled, dynamicColor = dynamic) {
                MuraiAppRoot(container = container, incomingUri = incomingUri)
            }
        }
    }
}
