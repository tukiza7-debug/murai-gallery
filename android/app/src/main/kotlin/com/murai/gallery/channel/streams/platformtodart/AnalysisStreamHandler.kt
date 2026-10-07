package com.murai.gallery.channel.streams.platformtodart

import com.murai.gallery.channel.streams.BaseStreamHandler
import com.murai.gallery.utils.LogUtils

class AnalysisStreamHandler : BaseStreamHandler() {
    fun notifyCompletion() = success(true)

    override val logTag = LOG_TAG

    companion object {
        private val LOG_TAG = LogUtils.createTag<AnalysisStreamHandler>()
        const val CHANNEL = "com.murai.gallery/analysis_events"
    }
}