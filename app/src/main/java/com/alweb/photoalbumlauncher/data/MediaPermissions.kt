package com.alweb.photoalbumlauncher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** How much of the photo library we are currently allowed to read. */
enum class MediaAccess {
    /** Every image is visible. */
    FULL,

    /** Android 14+ "Select photos": only user-picked images are visible. */
    PARTIAL,

    /** Nothing is visible. */
    DENIED,
}

object MediaPermissions {

    /** Permissions to ask for, which differ by platform version. */
    val required: Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
        )

        else -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
        )
    }

    fun check(context: Context): MediaAccess {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.isGranted(Manifest.permission.READ_MEDIA_IMAGES)) return MediaAccess.FULL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                context.isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            ) {
                return MediaAccess.PARTIAL
            }
            return MediaAccess.DENIED
        }
        return if (context.isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            MediaAccess.FULL
        } else {
            MediaAccess.DENIED
        }
    }

    private fun Context.isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
