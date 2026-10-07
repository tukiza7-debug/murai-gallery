package com.murai.gallery.channel.streams.platformtodart

import com.murai.gallery.channel.streams.BaseStreamHandler
import com.murai.gallery.utils.LogUtils

class IntentStreamHandler : BaseStreamHandler() {
    fun notifyNewIntent(intentData: MutableMap<String, Any?>?) = success(intentData)

    override val logTag = LOG_TAG

    companion object {
        private val LOG_TAG = LogUtils.createTag<IntentStreamHandler>()
        const val CHANNEL = "com.murai.gallery/new_intent_stream"
    }
}