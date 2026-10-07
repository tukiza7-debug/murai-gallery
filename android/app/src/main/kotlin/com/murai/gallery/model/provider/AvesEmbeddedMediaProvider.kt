package com.murai.gallery.model.provider

import android.content.Context
import android.net.Uri
import com.murai.gallery.utils.UriUtils.isContentScheme

class AvesEmbeddedMediaProvider : UnknownContentProvider() {
    override val reliableProviderMimeType: Boolean
        get() = true

    companion object {
        fun provides(context: Context, uri: Uri): Boolean {
            if (!uri.isContentScheme) return false
            return uri.authority == "${context.applicationContext.packageName}.file_provider"
        }
    }
}