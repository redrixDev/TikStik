// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.di

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import androidx.core.content.getSystemService
import androidx.work.WorkManager
import com.redrixdev.tikstik.BuildConfig
import com.redrixdev.tikstik.core.common.ApplicationScope
import com.redrixdev.tikstik.core.common.Clock
import com.redrixdev.tikstik.core.common.ConversionDispatcher
import com.redrixdev.tikstik.core.common.DefaultDispatcher
import com.redrixdev.tikstik.core.common.IoDispatcher
import com.redrixdev.tikstik.core.common.Logger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    fun clock(): Clock = Clock(System::currentTimeMillis)

    @Provides
    @IoDispatcher
    fun io(): CoroutineDispatcher = Dispatchers.IO

    // One thread on low-end phones (low RAM or 4 cores or fewer) and two elsewhere; released when idle.
    @Provides
    @Singleton
    @ConversionDispatcher
    fun conversion(@ApplicationContext context: Context): CoroutineDispatcher {
        val lowEnd = context.getSystemService<ActivityManager>()?.isLowRamDevice == true || Runtime.getRuntime().availableProcessors() <= 4
        val threads = if (lowEnd) 1 else 2
        val executor = ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS, LinkedBlockingQueue()) { task ->
            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                task.run()
            }, "tikstik-conversion")
        }
        executor.allowCoreThreadTimeOut(true)
        return executor.asCoroutineDispatcher()
    }

    @Provides
    @DefaultDispatcher
    fun cpu(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    // In release, a best-effort task failing does not crash the app; in debug it surfaces, so it is not overlooked.
    fun applicationScope(): CoroutineScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
                if (BuildConfig.DEBUG) throw e
                Logger.warn("App", "background task failed", e)
            },
        )

    @Provides
    @Singleton
    fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}
