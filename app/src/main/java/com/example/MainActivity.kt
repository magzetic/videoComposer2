package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.example.ui.VideoCompositorScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.VideoCompositorViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: VideoCompositorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure Coil ImageLoader with VideoFrameDecoder for thumbnail extraction
        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .bitmapConfig(android.graphics.Bitmap.Config.ARGB_8888)
            .allowHardware(true)
            .allowRgb565(false)
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)

        setContent {
            MyApplicationTheme {
                VideoCompositorScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

