package com.alweb.photoalbumlauncher

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alweb.photoalbumlauncher.data.MediaAccess
import com.alweb.photoalbumlauncher.data.MediaPermissions
import com.alweb.photoalbumlauncher.ui.AlbumGridScreen
import com.alweb.photoalbumlauncher.ui.AlbumPickerScreen
import com.alweb.photoalbumlauncher.ui.PermissionScreen
import com.alweb.photoalbumlauncher.ui.PhotoViewerScreen

/**
 * Single-activity root. There is no nav graph on purpose: the app is three states
 * (picker, album, viewer), and keeping them here makes the launch path
 * "read pref -> draw grid" with nothing in between.
 */
@Composable
fun AlbumApp() {
    val viewModel: AlbumViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var permissionRequested by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionRequested = true
        viewModel.refreshAccess()
    }
    val requestPermission = { permissionLauncher.launch(MediaPermissions.required) }

    // Access can change while we are backgrounded (via Settings, or the Android 14+
    // "selected photos" sheet), so re-read it on every resume.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshAccess()
        onPauseOrDispose {}
    }

    // The picker needs the album list; load it as soon as it is on screen.
    LaunchedEffect(state.showPicker, state.access) {
        if (state.showPicker && state.access != MediaAccess.DENIED) viewModel.loadAlbums()
    }

    // Leaving the picker is only allowed when there is an album to fall back to.
    if (state.pickerOpen && state.savedAlbum != null && viewerIndex == null) {
        BackHandler { viewModel.closePicker() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (state.access == MediaAccess.DENIED) {
            val activity = context as? Activity
            val permanentlyDenied = permissionRequested && activity != null &&
                MediaPermissions.required.none {
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
                }

            PermissionScreen(
                permanentlyDenied = permanentlyDenied,
                onRequestPermission = requestPermission,
            )
        } else {
            when {
                // Draw nothing until DataStore answers; this avoids a picker flash
                // on launch for users who already chose an album.
                !state.prefsLoaded -> Unit

                state.showPicker -> AlbumPickerScreen(
                    albums = state.albums,
                    loaded = state.albumsLoaded,
                    selectedAlbumId = state.savedAlbum?.id,
                    onAlbumClick = viewModel::selectAlbum,
                    // Android 14+ only: the user granted a subset of their library.
                    onSelectMorePhotos = requestPermission
                        .takeIf { state.access == MediaAccess.PARTIAL },
                )

                else -> AlbumGridScreen(
                    photos = state.photos,
                    loaded = state.photosLoaded,
                    onPhotoClick = { index -> viewerIndex = index },
                    onChangeAlbum = viewModel::openPicker,
                )
            }

            viewerIndex?.let { index ->
                PhotoViewerScreen(
                    photos = state.photos,
                    initialIndex = index,
                    onClose = { viewerIndex = null },
                )
            }
        }
    }
}
