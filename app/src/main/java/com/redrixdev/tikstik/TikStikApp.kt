// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.serviceLoaderEnabled
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

// WorkManager starts on first use with the Hilt factory (the default initializer is removed in the manifest).
@HiltAndroidApp
class TikStikApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    // Android 9's ImageDecoder does not open animated WebP (blank thumbnail): there they show still, as on Android 8.
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .serviceLoaderEnabled(false)
        .components { add(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) AnimatedImageDecoder.Factory() else GifDecoder.Factory()) }
        .build()
}
