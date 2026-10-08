package com.murai.gallery.ui.screens.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.murai.gallery.BuildConfig
import com.murai.gallery.R
import com.murai.gallery.di.AppContainer
import com.murai.gallery.ui.components.ToolScaffold

/** Fresh About screen for the v2.0.0 major release. */
@Composable
fun AboutScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    ToolScaffold(title = stringResource(R.string.settings_about), onBack = onBack) { padding ->
        Column(
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_splash_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(110.dp)
            )
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/tukiza7-debug/murai-gallery")))
                }
            }) { Text(stringResource(R.string.about_github)) }

            AboutCard(stringResource(R.string.about_terms_title)) {
                Text(stringResource(R.string.about_terms_text), style = MaterialTheme.typography.bodySmall)
            }
            AboutCard(stringResource(R.string.about_privacy_title)) {
                Text(stringResource(R.string.about_privacy_text), style = MaterialTheme.typography.bodySmall)
            }
            AboutCard(stringResource(R.string.about_libraries_title)) {
                Text(stringResource(R.string.about_libraries_text), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AboutCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.padding(vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}
