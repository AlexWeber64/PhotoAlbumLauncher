package com.alweb.photoalbumlauncher.data

import android.net.Uri

/** A MediaStore image bucket (what the gallery calls an "album"). */
data class Album(
    val id: Long,
    val name: String,
    val photoCount: Int,
    val coverUri: Uri,
)

/** A single image in an album. */
data class Photo(
    val id: Long,
    val uri: Uri,
)

/** The album the user picked as their home view, persisted across launches. */
data class SavedAlbum(
    val id: Long,
    val name: String,
)
