package com.alweb.photoalbumlauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.albumDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "album_prefs",
)

/** Persists which album is the home view. */
class AlbumPreferences(context: Context) {

    private val dataStore = context.applicationContext.albumDataStore

    val savedAlbum: Flow<SavedAlbum?> = dataStore.data.map { prefs ->
        val id = prefs[KEY_ALBUM_ID] ?: return@map null
        SavedAlbum(id = id, name = prefs[KEY_ALBUM_NAME].orEmpty())
    }

    suspend fun setSavedAlbum(album: Album) {
        dataStore.edit { prefs ->
            prefs[KEY_ALBUM_ID] = album.id
            prefs[KEY_ALBUM_NAME] = album.name
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_ALBUM_ID)
            prefs.remove(KEY_ALBUM_NAME)
        }
    }

    private companion object {
        val KEY_ALBUM_ID = longPreferencesKey("album_id")
        val KEY_ALBUM_NAME = stringPreferencesKey("album_name")
    }
}
