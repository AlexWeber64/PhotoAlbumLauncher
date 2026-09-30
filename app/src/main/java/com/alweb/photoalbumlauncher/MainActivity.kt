package com.alweb.photoalbumlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.alweb.photoalbumlauncher.ui.theme.PhotoAlbumTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PhotoAlbumTheme {
                AlbumApp()
            }
        }
    }
}
