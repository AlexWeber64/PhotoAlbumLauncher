package com.alweb.photoalbumlauncher

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import coil.request.CachePolicy

class PhotoAlbumApplication : Application(), ImageLoaderFactory {

    /**
     * Thumbnails come from local files, so a disk cache would only duplicate them.
     * A generous memory cache is what actually makes the grid feel instant.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.30)
                .build()
        }
        .diskCachePolicy(CachePolicy.DISABLED)
        .crossfade(false)
        .build()
}
