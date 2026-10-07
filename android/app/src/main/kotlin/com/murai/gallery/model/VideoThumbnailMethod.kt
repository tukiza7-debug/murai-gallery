package com.murai.gallery.model

enum class VideoThumbnailMethod {
    EMBEDDED, PREVIEW, FIRST;

    companion object {
        fun fromKey(name: String?): VideoThumbnailMethod? {
            name ?: return null
            return valueOf(name.uppercase())
        }
    }
}
