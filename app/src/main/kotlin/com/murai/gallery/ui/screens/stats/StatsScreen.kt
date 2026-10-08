package com.murai.gallery.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.murai.gallery.R
import com.murai.gallery.di.AppContainer
import com.murai.gallery.domain.model.StatsSummary
import com.murai.gallery.util.Formatters
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(container: AppContainer, onBack: () -> Unit) {
    var summary by remember { mutableStateOf<StatsSummary?>(null) }
    LaunchedEffect(Unit) {
        val dao = container.db.libraryDao()
        val folders = dao.folderCounts()
        val months = dao.monthCounts()
        val mimes = dao.mimeCounts()
        summary = StatsSummary(
            totalItems = dao.count(),
            totalImages = dao.countByType(false),
            totalVideos = dao.countByType(true),
            totalBytes = dao.totalBytes(),
            folders = folders.size,
            longestVideoMs = dao.longestVideo()?.durationMs ?: 0L,
            biggestFileBytes = dao.biggestSize(),
            oldestDateSec = dao.oldestDate() ?: 0L,
            newestDateSec = dao.newestDate() ?: 0L,
            byFolder = folders.map { it.folder to it.c },
            byMonth = months.map { it.m to it.c },
            byType = mimes.map { it.mime to it.c }
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        val s = summary
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (s == null) {
                Text(stringResource(R.string.loading), modifier = Modifier.padding(24.dp))
            } else {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.stats_library), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        StatRow(stringResource(R.string.stats_total), NumberFormat.getIntegerInstance().format(s.totalItems))
                        StatRow(stringResource(R.string.stats_images), NumberFormat.getIntegerInstance().format(s.totalImages))
                        StatRow(stringResource(R.string.stats_videos), NumberFormat.getIntegerInstance().format(s.totalVideos))
                        StatRow(stringResource(R.string.stats_size), Formatters.fileSize(s.totalBytes))
                        StatRow(stringResource(R.string.stats_folders), s.folders.toString())
                        StatRow(stringResource(R.string.stats_span), Formatters.date(s.oldestDateSec) + " → " + Formatters.date(s.newestDateSec))
                        StatRow(stringResource(R.string.stats_largest), Formatters.fileSize(s.biggestFileBytes))
                        StatRow(stringResource(R.string.stats_longest), Formatters.duration(s.longestVideoMs))
                    }
                }
                if (s.byMonth.isNotEmpty()) {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.stats_by_month), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(10.dp))
                            BarChart(
                                data = s.byMonth.reversed(),
                                barColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                            )
                        }
                    }
                }
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.stats_top_folders), style = MaterialTheme.typography.titleMedium)
                        s.byFolder.take(8).forEach { (folder, count) ->
                            StatRow(folder, count.toString())
                        }
                    }
                }
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.stats_by_type), style = MaterialTheme.typography.titleMedium)
                        s.byType.forEach { (mime, count) ->
                            StatRow(mime, count.toString())
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BarChart(data: List<Pair<String, Int>>, barColor: Color, modifier: Modifier = Modifier) {
    if (data.isEmpty()) return
    val max = data.maxOf { it.second }.coerceAtLeast(1)
    val measurer = rememberTextMeasurer()
    Canvas(modifier = modifier) {
        val barW = size.width / data.size
        data.forEachIndexed { i, (label, count) ->
            val h = size.height * (count.toFloat() / max) * 0.85f
            drawRect(
                color = barColor,
                topLeft = Offset(i * barW + barW * 0.12f, size.height - h - 18.sp.toPx()),
                size = Size(barW * 0.76f, h)
            )
            drawText(
                textMeasurer = measurer,
                text = label.substringAfter('-'),
                topLeft = Offset(i * barW + barW * 0.12f, size.height - 16.sp.toPx()),
                style = androidx.compose.ui.text.TextStyle(fontSize = 8.sp, color = barColor)
            )
        }
    }
}
