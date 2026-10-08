package com.murai.gallery.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Creates a ViewModel whose constructor needs the app container. */
inline fun <reified T : ViewModel> muraiFactory(crossinline create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <V : ViewModel> create(modelClass: Class<V>): V {
            @Suppress("UNCHECKED_CAST")
            return create() as V
        }
    }
