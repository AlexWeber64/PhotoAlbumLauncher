package com.alweb.photoalbumlauncher.data

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Reads albums and photos straight out of MediaStore. No local database, no sync. */
class MediaStoreRepository(context: Context) {

    private val resolver = context.applicationContext.contentResolver

    private val collection: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    /** Every distinct bucket, newest photo first, with that photo as the cover. */
    suspend fun queryAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )

        // MediaStore has no portable GROUP BY, so we fold the (already sorted) rows ourselves.
        // The first row seen for a bucket is its newest image and becomes the cover.
        val covers = LinkedHashMap<Long, Album>()

        query(projection, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                if (cursor.isNull(bucketIdColumn)) continue
                val bucketId = cursor.getLong(bucketIdColumn)
                val existing = covers[bucketId]
                if (existing == null) {
                    val imageId = cursor.getLong(idColumn)
                    covers[bucketId] = Album(
                        id = bucketId,
                        name = cursor.getString(bucketNameColumn) ?: UNNAMED_ALBUM,
                        photoCount = 1,
                        coverUri = ContentUris.withAppendedId(collection, imageId),
                    )
                } else {
                    covers[bucketId] = existing.copy(photoCount = existing.photoCount + 1)
                }
            }
        }

        covers.values.sortedBy { it.name.lowercase() }
    }

    /** All images in one bucket, newest first. */
    suspend fun queryPhotos(bucketId: Long): List<Photo> = withContext(Dispatchers.IO) {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(bucketId.toString())

        val photos = mutableListOf<Photo>()
        query(projection, selection, selectionArgs)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                photos += Photo(id = id, uri = ContentUris.withAppendedId(collection, id))
            }
        }
        photos
    }

    /**
     * Permission can be revoked between our check and this call, and a revoked read
     * throws rather than returning an empty cursor. Treat it as "nothing visible";
     * the resume handler re-checks access and the UI falls back to the rationale.
     */
    private fun query(
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?,
    ) = try {
        resolver.query(collection, projection, selection, selectionArgs, SORT_NEWEST_FIRST)
    } catch (e: SecurityException) {
        Log.w(TAG, "Media query denied", e)
        null
    }

    /** Emits immediately, then again whenever the image collection changes. */
    private fun mediaChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(collection, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()

    fun albumsStream(): Flow<List<Album>> =
        mediaChanges().map { queryAlbums() }.flowOn(Dispatchers.IO)

    fun photosStream(bucketId: Long): Flow<List<Photo>> =
        mediaChanges().map { queryPhotos(bucketId) }.flowOn(Dispatchers.IO)

    private companion object {
        const val SORT_NEWEST_FIRST =
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC, ${MediaStore.Images.Media._ID} DESC"
        const val UNNAMED_ALBUM = "Unnamed"
        const val TAG = "MediaStoreRepository"
    }
}
