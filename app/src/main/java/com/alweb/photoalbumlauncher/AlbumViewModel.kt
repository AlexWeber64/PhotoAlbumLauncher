package com.alweb.photoalbumlauncher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alweb.photoalbumlauncher.data.Album
import com.alweb.photoalbumlauncher.data.AlbumPreferences
import com.alweb.photoalbumlauncher.data.MediaAccess
import com.alweb.photoalbumlauncher.data.MediaPermissions
import com.alweb.photoalbumlauncher.data.MediaStoreRepository
import com.alweb.photoalbumlauncher.data.Photo
import com.alweb.photoalbumlauncher.data.SavedAlbum
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val access: MediaAccess = MediaAccess.DENIED,
    val savedAlbum: SavedAlbum? = null,
    /** False until DataStore has answered, so we never flash the picker on a cold start. */
    val prefsLoaded: Boolean = false,
    val pickerOpen: Boolean = false,
    val albums: List<Album> = emptyList(),
    val albumsLoaded: Boolean = false,
    val photos: List<Photo> = emptyList(),
    val photosLoaded: Boolean = false,
) {
    /** Show the picker on first launch, or whenever the user asks to change albums. */
    val showPicker: Boolean get() = pickerOpen || (prefsLoaded && savedAlbum == null)
}

class AlbumViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaStoreRepository(application)
    private val preferences = AlbumPreferences(application)

    private val _state = MutableStateFlow(UiState(access = MediaPermissions.check(application)))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var photosJob: Job? = null
    private var albumsJob: Job? = null

    init {
        viewModelScope.launch {
            preferences.savedAlbum.collect { saved ->
                val changed = _state.value.savedAlbum?.id != saved?.id
                _state.update { it.copy(savedAlbum = saved, prefsLoaded = true) }
                if (changed) observePhotos(saved?.id)
            }
        }
    }

    /** Re-checked on every resume, since the user can revoke access from Settings. */
    fun refreshAccess() {
        val access = MediaPermissions.check(getApplication<Application>())
        val changed = _state.value.access != access
        _state.update { it.copy(access = access) }
        if (changed && access != MediaAccess.DENIED) {
            // A stream opened while access was denied returned nothing and will never
            // be woken by the ContentObserver, so restart rather than reuse it.
            observePhotos(_state.value.savedAlbum?.id)
            if (_state.value.showPicker) loadAlbums(restart = true)
        }
    }

    fun loadAlbums(restart: Boolean = false) {
        if (!restart && albumsJob?.isActive == true) return
        albumsJob?.cancel()
        if (restart) _state.update { it.copy(albumsLoaded = false) }
        albumsJob = viewModelScope.launch {
            repository.albumsStream().collect { albums ->
                _state.update { it.copy(albums = albums, albumsLoaded = true) }
            }
        }
    }

    private fun observePhotos(bucketId: Long?) {
        photosJob?.cancel()
        if (bucketId == null) {
            _state.update { it.copy(photos = emptyList(), photosLoaded = false) }
            return
        }
        _state.update { it.copy(photos = emptyList(), photosLoaded = false) }
        photosJob = viewModelScope.launch {
            repository.photosStream(bucketId).collect { photos ->
                _state.update { it.copy(photos = photos, photosLoaded = true) }
            }
        }
    }

    fun openPicker() {
        _state.update { it.copy(pickerOpen = true) }
        loadAlbums()
    }

    /** Only closable when an album is already saved; first launch must pick one. */
    fun closePicker() {
        if (_state.value.savedAlbum != null) {
            _state.update { it.copy(pickerOpen = false) }
        }
    }

    fun selectAlbum(album: Album) {
        _state.update { it.copy(pickerOpen = false) }
        viewModelScope.launch { preferences.setSavedAlbum(album) }
    }
}
